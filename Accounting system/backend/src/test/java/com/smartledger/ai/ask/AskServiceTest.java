package com.smartledger.ai.ask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartledger.ai.DeepSeekClient;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.AskAnswerDTO;
import com.smartledger.service.AiParseLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 问答编排单测：模型输出解析、SQL 守卫联动、回答生成失败兜底。
 */
@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    @Mock
    private DeepSeekClient deepSeekClient;

    @Mock
    private AskPromptBuilder askPromptBuilder;

    @Mock
    private BillQueryExecutor billQueryExecutor;

    @Mock
    private AiParseLogService aiParseLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AskService askService;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setRateLimitPerMinute(0);
        properties.setModel("deepseek-flash");
        askService = new AskService(deepSeekClient, askPromptBuilder, billQueryExecutor,
                aiParseLogService, properties, objectMapper);
    }

    @Test
    @DisplayName("正常链路：模型 SQL 经守卫补全后执行，回答沿用模型输出")
    void shouldAnswerWithModelSqlAndAnswer() {
        when(askPromptBuilder.buildSqlPrompt()).thenReturn("sql-prompt");
        when(askPromptBuilder.buildAnswerPrompt(anyString())).thenReturn("answer-prompt");
        when(deepSeekClient.chatContent(eq("sql-prompt"), anyString(), anyInt())).thenReturn(result(
                "{\"sql\":\"SELECT ROUND(SUM(amount), 2) AS total FROM bill WHERE openid = :openid AND bill_type = 0\"}"));
        when(deepSeekClient.chatContent(eq("answer-prompt"), anyString(), anyInt())).thenReturn(result(
                "{\"answer\":\"这个月支出一共 4366.50 元。\"}"));
        when(billQueryExecutor.execute(eq("openid-1"), anyString()))
                .thenReturn(List.of(Map.of("total", new BigDecimal("4366.50"))));

        AskAnswerDTO dto = askService.answer("openid-1", "这个月花了多少");

        assertThat(dto.getAnswer()).isEqualTo("这个月支出一共 4366.50 元。");
        assertThat(dto.getSql()).contains("deleted = 0").endsWith("LIMIT 100");
        assertThat(dto.getRows()).hasSize(1);
        verify(billQueryExecutor).execute("openid-1", dto.getSql());
    }

    @Test
    @DisplayName("模型输出危险 SQL 时拒绝执行")
    void shouldRejectUnsafeSql() {
        when(askPromptBuilder.buildSqlPrompt()).thenReturn("sql-prompt");
        when(deepSeekClient.chatContent(eq("sql-prompt"), anyString(), anyInt())).thenReturn(result(
                "{\"sql\":\"DELETE FROM bill WHERE openid = :openid\"}"));

        assertThatThrownBy(() -> askService.answer("openid-1", "删掉我的账单"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(ErrorCode.AI_SQL_REJECTED.getCode());
        verifyNoInteractions(billQueryExecutor);
    }

    @Test
    @DisplayName("回答模型失败时用结果集模板兜底，接口仍然可用")
    void shouldFallbackWhenAnswerModelFails() {
        when(askPromptBuilder.buildSqlPrompt()).thenReturn("sql-prompt");
        when(askPromptBuilder.buildAnswerPrompt(anyString())).thenReturn("answer-prompt");
        when(deepSeekClient.chatContent(eq("sql-prompt"), anyString(), anyInt())).thenReturn(result(
                "{\"sql\":\"SELECT ROUND(SUM(amount), 2) AS total FROM bill WHERE openid = :openid\"}"));
        when(deepSeekClient.chatContent(eq("answer-prompt"), anyString(), anyInt()))
                .thenThrow(new BizException(ErrorCode.AI_PARSE_FAILED, "超时"));
        when(billQueryExecutor.execute(eq("openid-1"), anyString()))
                .thenReturn(List.of(Map.of("total", new BigDecimal("4366.50"))));

        AskAnswerDTO dto = askService.answer("openid-1", "这个月花了多少");

        assertThat(dto.getAnswer()).contains("4366.50").contains("元");
    }

    @Test
    @DisplayName("查询无结果时给出无账单提示")
    void shouldSayNoRecordsWhenEmpty() {
        when(askPromptBuilder.buildSqlPrompt()).thenReturn("sql-prompt");
        when(askPromptBuilder.buildAnswerPrompt(anyString())).thenReturn("answer-prompt");
        when(deepSeekClient.chatContent(eq("sql-prompt"), anyString(), anyInt())).thenReturn(result(
                "{\"sql\":\"SELECT COUNT(*) AS cnt FROM bill WHERE openid = :openid AND record_date = '2000-01-01'\"}"));
        when(deepSeekClient.chatContent(eq("answer-prompt"), anyString(), anyInt())).thenReturn(result("{\"answer\":\"\"}"));
        when(billQueryExecutor.execute(eq("openid-1"), anyString())).thenReturn(List.of());

        AskAnswerDTO dto = askService.answer("openid-1", "2000 年花了多少");

        assertThat(dto.getAnswer()).contains("没有查到");
    }

    private DeepSeekResult result(String content) {
        DeepSeekResult result = new DeepSeekResult();
        result.setContent(content);
        result.setPromptTokens(10);
        result.setCompletionTokens(10);
        return result;
    }
}
