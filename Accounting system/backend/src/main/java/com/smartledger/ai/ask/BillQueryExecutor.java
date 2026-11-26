package com.smartledger.ai.ask;

import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 账单只读查询执行器。
 *
 * <p>三层保护：{@link SqlGuard} 校验 SQL（只读、单表、强制 openid 参数）、
 * 只读事务（数据库连接级禁止写入）、执行超时与最大行数限制。</p>
 */
@Slf4j
@Component
public class BillQueryExecutor {

    /** 单次查询超时（秒），防止模型写出全表扫描拖垮接口 */
    private static final int QUERY_TIMEOUT_SECONDS = 3;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillQueryExecutor(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        jdbc.setMaxRows(SqlGuard.MAX_LIMIT);
        this.jdbcTemplate = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * 执行已通过 {@link SqlGuard#normalize(String)} 的 SQL。
     *
     * @param openid 当前登录用户，绑定到 SQL 里的 :openid，调用方无法伪造
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String openid, String safeSql) {
        MapSqlParameterSource params = new MapSqlParameterSource(SqlGuard.OPENID_PARAM, openid);
        try {
            return jdbcTemplate.queryForList(safeSql, params);
        } catch (DataAccessException e) {
            log.warn("问答 SQL 执行失败: sql={}, error={}", safeSql, e.getMostSpecificCause().getMessage());
            throw new BizException(ErrorCode.AI_OUTPUT_INVALID, "查询执行失败，请换个问法试试");
        }
    }
}
