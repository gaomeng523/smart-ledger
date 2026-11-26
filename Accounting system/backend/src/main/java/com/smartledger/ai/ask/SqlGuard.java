package com.smartledger.ai.ask;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 守卫：把大模型生成的 SQL 收敛成「单条 SELECT + 表名白名单 + 用户隔离 + 行数上限」。
 *
 * <p>模型输出不可信，因此除了 Prompt 约束外，这里再做一层硬校验：
 * <ol>
 *   <li>只能是一条 SELECT，禁止分号多语句、注释、UNION 与任何写操作关键字；</li>
 *   <li>FROM / JOIN 只允许 {@code bill} 表；</li>
 *   <li>必须出现 {@code openid = :openid}，openid 由服务端以命名参数绑定，模型无法指定他人数据；</li>
 *   <li>自动补 {@code deleted = 0}（只统计未删除账单），并强制收敛 LIMIT。</li>
 * </ol>
 */
public final class SqlGuard {

    /** 单次查询最大返回行数 */
    public static final int MAX_LIMIT = 200;

    /** 未写 LIMIT 时补的默认行数 */
    public static final int DEFAULT_LIMIT = 100;

    /** 服务端绑定的用户参数名 */
    public static final String OPENID_PARAM = "openid";

    private static final String ALLOWED_TABLE = "bill";

    /** 任何位置出现即拒绝的关键字（写操作、DDL、危险函数、跨库） */
    private static final Set<String> FORBIDDEN_WORDS = Set.of(
            "insert", "update", "delete", "drop", "alter", "truncate", "create",
            "grant", "revoke", "rename", "replace", "call", "execute", "handler",
            "load_file", "outfile", "dumpfile", "sleep", "benchmark", "union",
            "information_schema", "performance_schema", "procedure", "trigger");

    private static final Pattern WORD = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Pattern FROM_CLAUSE = Pattern.compile(
            "\\bfrom\\b(.*?)(?=\\bwhere\\b|\\bgroup\\s+by\\b|\\border\\s+by\\b|\\blimit\\b|\\bhaving\\b|\\bjoin\\b|\\bunion\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern JOIN_TABLE = Pattern.compile(
            "\\bjoin\\s+`?([A-Za-z_][A-Za-z0-9_.]*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern OPENID_BOUND = Pattern.compile(
            "\\bopenid\\s*=\\s*:openid\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern DELETED_COMPARE = Pattern.compile(
            "\\bdeleted\\s*(<=|>=|<>|!=|=|<|>)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern WHERE_CLAUSE = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAIL_CLAUSE = Pattern.compile(
            "\\b(group\\s+by|order\\s+by|having|limit)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LIMIT_CLAUSE = Pattern.compile(
            "\\blimit\\s+(\\d+)", Pattern.CASE_INSENSITIVE);

    private SqlGuard() {
    }

    /**
     * 校验并归一化模型生成的 SQL。
     *
     * @return 可直接交给参数化执行器执行的 SQL（已补 deleted 条件与 LIMIT）
     * @throws BizException 未通过校验时抛出 {@link ErrorCode#AI_SQL_REJECTED}
     */
    public static String normalize(String rawSql) {
        String sql = stripCodeFence(rawSql);
        if (sql.isEmpty()) {
            throw reject("模型未返回查询语句");
        }
        sql = stripTrailingSemicolon(sql);

        if (sql.contains(";")) {
            throw reject("不允许多条语句");
        }
        if (sql.contains("--") || sql.contains("/*") || sql.contains("#")) {
            throw reject("不允许 SQL 注释");
        }
        if (!sql.regionMatches(true, 0, "select", 0, 6)) {
            throw reject("只允许 SELECT 查询");
        }

        Matcher words = WORD.matcher(sql);
        while (words.find()) {
            if (FORBIDDEN_WORDS.contains(words.group().toLowerCase(Locale.ROOT))) {
                throw reject("不允许的关键字: " + words.group());
            }
        }

        checkTables(sql);
        checkOpenid(sql);
        sql = checkDeleted(sql);
        sql = clampLimit(sql);
        return sql.trim();
    }

    private static String stripCodeFence(String rawSql) {
        if (rawSql == null) {
            return "";
        }
        String sql = rawSql.trim();
        if (!sql.startsWith("```")) {
            return sql;
        }
        int firstLineEnd = sql.indexOf('\n');
        if (firstLineEnd < 0) {
            return "";
        }
        sql = sql.substring(firstLineEnd + 1);
        int fence = sql.lastIndexOf("```");
        if (fence >= 0) {
            sql = sql.substring(0, fence);
        }
        return sql.trim();
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** FROM / JOIN 只允许 bill 表（含逗号连接的多表写法） */
    private static void checkTables(String sql) {
        Matcher from = FROM_CLAUSE.matcher(sql);
        while (from.find()) {
            for (String part : from.group(1).split(",")) {
                String table = firstWord(part);
                if (table != null && !ALLOWED_TABLE.equalsIgnoreCase(table)) {
                    throw reject("不允许访问的表: " + table);
                }
            }
        }
        Matcher join = JOIN_TABLE.matcher(sql);
        while (join.find()) {
            String table = join.group(1).replace("`", "");
            if (!ALLOWED_TABLE.equalsIgnoreCase(table)) {
                throw reject("不允许访问的表: " + table);
            }
        }
    }

    private static String firstWord(String text) {
        Matcher matcher = WORD.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 必须有且只有 {@code openid = :openid}：既保证每个查询都带用户隔离，
     * 也避免模型把别人的 openid 写死进 SQL。
     */
    private static void checkOpenid(String sql) {
        int bound = countMatches(OPENID_BOUND, sql);
        if (bound == 0) {
            throw reject("查询必须包含 openid = :openid 条件");
        }
        int plain = 0;
        Matcher word = WORD.matcher(sql);
        while (word.find()) {
            if (!"openid".equalsIgnoreCase(word.group())) {
                continue;
            }
            // ":openid" 是参数名，不算模型自己引用了 openid 字段
            if (word.start() > 0 && sql.charAt(word.start() - 1) == ':') {
                continue;
            }
            plain++;
        }
        if (plain != bound) {
            throw reject("openid 条件必须写成 openid = :openid，禁止写死用户标识");
        }
    }

    /** 只统计未删除账单：缺条件时自动补，写错（如 deleted = 1）直接拒绝 */
    private static String checkDeleted(String sql) {
        Matcher compare = DELETED_COMPARE.matcher(sql);
        boolean present = false;
        while (compare.find()) {
            present = true;
            if (!"=".equals(compare.group(1)) || !"0".equals(compare.group(2))) {
                throw reject("deleted 条件只能是 deleted = 0");
            }
        }
        if (present || containsWord(sql, "deleted")) {
            return sql;
        }
        Matcher where = WHERE_CLAUSE.matcher(sql);
        if (where.find()) {
            return sql.substring(0, where.end()) + " deleted = 0 AND" + sql.substring(where.end());
        }
        Matcher tail = TAIL_CLAUSE.matcher(sql);
        if (tail.find()) {
            return sql.substring(0, tail.start()) + "WHERE deleted = 0 " + sql.substring(tail.start());
        }
        return sql + " WHERE deleted = 0";
    }

    private static String clampLimit(String sql) {
        Matcher limit = LIMIT_CLAUSE.matcher(sql);
        if (!limit.find()) {
            return sql + " LIMIT " + DEFAULT_LIMIT;
        }
        int value;
        try {
            value = Integer.parseInt(limit.group(1));
        } catch (NumberFormatException e) {
            throw reject("LIMIT 取值非法");
        }
        if (value > MAX_LIMIT) {
            return sql.substring(0, limit.start(1)) + MAX_LIMIT + sql.substring(limit.end(1));
        }
        return sql;
    }

    private static boolean containsWord(String sql, String word) {
        Matcher matcher = WORD.matcher(sql);
        while (matcher.find()) {
            if (word.equalsIgnoreCase(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static BizException reject(String reason) {
        return new BizException(ErrorCode.AI_SQL_REJECTED, ErrorCode.AI_SQL_REJECTED.getMessage() + "：" + reason);
    }
}
