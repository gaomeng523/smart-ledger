package com.smartledger.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 智能账单问答结果。
 */
@Data
public class AskAnswerDTO {

    /** 自然语言回答 */
    private String answer;

    /** 实际执行的只读 SQL（已过安全校验，便于排查与评测） */
    private String sql;

    /** 查询结果集（最多 200 行） */
    private List<Map<String, Object>> rows;

    /** 端到端耗时（毫秒） */
    private long latencyMs;
}
