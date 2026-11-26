package com.smartledger.ai.ask;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 只读 SQL 守卫单测：覆盖放行、补全与各类越权/写操作拦截。
 */
class SqlGuardTest {

    private static final String MONTH_RANGE =
            " AND record_date >= '2026-11-01' AND record_date < '2026-12-01'";

    private static final String SAFE_AGGREGATE =
            "SELECT ROUND(SUM(amount), 2) AS total FROM bill WHERE openid = :openid"
                    + " AND bill_type = 0" + MONTH_RANGE;

    @Test
    @DisplayName("正常聚合查询放行，并自动补 deleted = 0 与 LIMIT")
    void shouldPassAggregateAndInjectDeleted() {
        String safe = SqlGuard.normalize(SAFE_AGGREGATE);

        assertThat(safe).startsWith("SELECT");
        assertThat(safe).contains("WHERE deleted = 0 AND openid = :openid");
        assertThat(safe).endsWith("LIMIT 100");
        assertThat(safe).doesNotContain(";");
    }

    @Test
    @DisplayName("已写 deleted = 0 时不重复注入")
    void shouldNotDuplicateDeletedCondition() {
        String safe = SqlGuard.normalize(
                "SELECT SUM(amount) AS total FROM bill WHERE openid = :openid AND deleted = 0");

        assertThat(safe).containsOnlyOnce("deleted = 0");
    }

    @Test
    @DisplayName("结尾单个分号允许，多语句拒绝")
    void shouldAllowSingleTrailingSemicolonButRejectMultipleStatements() {
        assertThat(SqlGuard.normalize(SAFE_AGGREGATE + ";")).startsWith("SELECT");

        assertThatThrownBy(() -> SqlGuard.normalize(
                "SELECT SUM(amount) FROM bill WHERE openid = :openid; DELETE FROM bill WHERE openid = :openid"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("多条语句");
    }

    @Test
    @DisplayName("写操作与 DDL 一律拒绝")
    void shouldRejectWriteStatements() {
        assertRejected("DELETE FROM bill WHERE openid = :openid");
        assertRejected("UPDATE bill SET amount = 0 WHERE openid = :openid");
        assertRejected("INSERT INTO bill (openid, amount) VALUES (:openid, 1)");
        assertRejected("DROP TABLE bill");
    }

    @Test
    @DisplayName("注释与 UNION 注入拒绝")
    void shouldRejectCommentAndUnion() {
        assertRejected("SELECT SUM(amount) FROM bill WHERE openid = :openid -- 注释");
        assertRejected("SELECT SUM(amount) FROM bill WHERE openid = :openid /* x */");
        assertRejected(SAFE_AGGREGATE + " UNION SELECT amount FROM bill WHERE openid = :openid");
    }

    @Test
    @DisplayName("非白名单表拒绝")
    void shouldRejectOtherTables() {
        assertRejected("SELECT nickname FROM user WHERE openid = :openid");
        assertRejected("SELECT b.amount FROM bill b JOIN user u ON u.openid = :openid");
        assertRejected("SELECT amount FROM bill, pay_channel WHERE openid = :openid");
    }

    @Test
    @DisplayName("缺少 openid 绑定条件拒绝")
    void shouldRequireOpenidParameter() {
        assertRejected("SELECT SUM(amount) FROM bill WHERE bill_type = 0");
    }

    @Test
    @DisplayName("写死 openid 字面量拒绝（防止越权查他人账单）")
    void shouldRejectLiteralOpenid() {
        assertRejected("SELECT SUM(amount) FROM bill WHERE openid = 'other_user'");
        assertRejected("SELECT SUM(amount) FROM bill WHERE openid = :openid OR openid = 'other_user'");
    }

    @Test
    @DisplayName("deleted 只能等于 0")
    void shouldRejectDeletedConditionOtherThanZero() {
        assertRejected("SELECT SUM(amount) FROM bill WHERE openid = :openid AND deleted = 1");
    }

    @Test
    @DisplayName("LIMIT 超过上限时收敛到 200")
    void shouldClampLimit() {
        String safe = SqlGuard.normalize(SAFE_AGGREGATE + " LIMIT 5000");

        assertThat(safe).endsWith("LIMIT 200");
    }

    @Test
    @DisplayName("普通查询不会被误判为危险 SQL")
    void shouldNotBlockNormalColumnNames() {
        String safe = SqlGuard.normalize(
                "SELECT category_code, SUM(amount) AS total, COUNT(*) AS cnt FROM bill"
                        + " WHERE openid = :openid AND deleted = 0" + MONTH_RANGE
                        + " GROUP BY category_code ORDER BY total DESC LIMIT 5");

        assertThat(safe).contains("GROUP BY category_code").endsWith("LIMIT 5");
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> SqlGuard.normalize(sql))
                .as("应当拒绝: %s", sql)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.AI_SQL_REJECTED.getCode());
    }
}
