package com.smartledger.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.config.AiProperties;
import com.smartledger.dto.response.AliasResolveDTO;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.ParseResultDTO;
import com.smartledger.service.AiParseLogService;
import com.smartledger.service.AliasService;
import com.smartledger.service.DictService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 解析编排（核心）：构建 Prompt → 调用模型 → 校验归一 → 记录日志 → 返回结果。
 *
 * <p>本服务只产出「待确认草稿」，不写账单表；落库由 BillService.save 负责。</p>
 */
@Slf4j
@Service
public class AiParseService {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");

    private final AiProperties aiProperties;
    private final PromptBuilder promptBuilder;
    private final DeepSeekClient deepSeekClient;
    private final ParseResultValidator validator;
    private final AiParseLogService aiParseLogService;
    private final AliasService aliasService;
    private final DictService dictService;

    private Cache<String, ParseResultDTO> resultCache;
    private Cache<String, AtomicInteger> rateLimitCache;

    public AiParseService(AiProperties aiProperties,
                          PromptBuilder promptBuilder,
                          DeepSeekClient deepSeekClient,
                          ParseResultValidator validator,
                          AiParseLogService aiParseLogService,
                          AliasService aliasService,
                          DictService dictService) {
        this.aiProperties = aiProperties;
        this.promptBuilder = promptBuilder;
        this.deepSeekClient = deepSeekClient;
        this.validator = validator;
        this.aiParseLogService = aiParseLogService;
        this.aliasService = aliasService;
        this.dictService = dictService;
    }

    @PostConstruct
    void initCaches() {
        this.resultCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, aiProperties.getCacheSeconds())))
                .maximumSize(1000)
                .build();
        this.rateLimitCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(10000)
                .build();
    }

    /**
     * 解析自然语言文本，返回待确认草稿。
     */
    public ParseResultDTO parse(String openid, String text) {
        String input = text == null ? "" : text.trim();
        if (!StringUtils.hasText(input)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "输入文本不能为空");
        }
        if (input.length() > aiProperties.getMaxInputLength()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "输入文本长度不能超过 " + aiProperties.getMaxInputLength() + " 字符");
        }

        checkRateLimit(openid);

        // V2 别名词典：解析前先做别名匹配；命中分类且能提取金额时直接返回，跳过 AI（省成本）
        AliasResolveDTO alias = aliasService.resolve(openid, input);
        if (alias.getCategoryCode() != null) {
            BigDecimal aliasAmount = extractAmount(input);
            if (aliasAmount.compareTo(BigDecimal.ZERO) > 0) {
                return buildFromAlias(input, alias, aliasAmount);
            }
        }

        String cacheKey = openid + "|" + input;
        ParseResultDTO cached = resultCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (!aiProperties.isEnabled()) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "AI 解析未启用，请手动填写", buildFallback(input));
        }

        long start = System.currentTimeMillis();
        DeepSeekResult result;
        try {
            result = deepSeekClient.chat(promptBuilder.buildSystemPrompt(), input);
        } catch (BizException e) {
            long cost = System.currentTimeMillis() - start;
            aiParseLogService.record(openid, input, null, aiProperties.getModel(), false,
                    String.valueOf(e.getCode()), e.getMessage(), cost, null, null);
            throw new BizException(e.getCode(), e.getMessage(), buildFallback(input));
        }

        long cost = System.currentTimeMillis() - start;
        ParseResultDTO dto = validator.normalize(result.getRaw());
        applyAlias(dto, alias);
        aiParseLogService.record(openid, input, result.getContent(), aiProperties.getModel(), true,
                null, null, cost, result.getPromptTokens(), result.getCompletionTokens());

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.AMOUNT_UNRECOGNIZED, ErrorCode.AMOUNT_UNRECOGNIZED.getMessage(), dto);
        }

        resultCache.put(cacheKey, dto);
        return dto;
    }

    private void checkRateLimit(String openid) {
        int limit = aiProperties.getRateLimitPerMinute();
        if (limit <= 0) {
            return;
        }
        AtomicInteger counter = rateLimitCache.get(openid, k -> new AtomicInteger());
        if (counter.incrementAndGet() > limit) {
            throw new BizException(ErrorCode.PARSE_TOO_FREQUENT);
        }
    }

    /**
     * AI 不可用时的兜底草稿：尽力从文本中提取金额、日期与支付渠道，其余字段留空待用户补全。
     */
    private ParseResultDTO buildFallback(String input) {
        ParseResultDTO dto = new ParseResultDTO();
        dto.setBillType(0);
        BigDecimal amount = extractAmount(input);
        dto.setAmount(amount);
        dto.setRecordDate(DateUtils.format(extractDate(input)));
        dto.setConfidence(BigDecimal.ZERO);
        dto.setNeedConfirm(true);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            dto.getMissingFields().add("amount");
        }
        // 渠道关键词兜底：原文明确出现「微信 / 支付宝 / 现金 / 银行卡」等时直接识别，
        // 不再把支付渠道丢给用户手选（与金额、日期同为规则提取）
        String channelCode = extractChannelCode(input);
        if (channelCode != null) {
            DictItemDTO channel = dictService.getChannel(channelCode);
            if (channel != null) {
                dto.setPayChannelCode(channel.getCode());
                dto.setPayChannelName(channel.getName());
            } else {
                dto.getMissingFields().add("payChannelCode");
            }
        } else {
            dto.getMissingFields().add("payChannelCode");
        }
        dto.getMissingFields().add("categoryCode");
        return dto;
    }

    /**
     * 支付渠道关键词规则匹配（仅匹配无歧义的常见说法）；无法确定时返回 null。
     */
    private String extractChannelCode(String input) {
        if (input.contains("支付宝") || input.toLowerCase().contains("alipay") || input.contains("花呗")) {
            return "ALIPAY";
        }
        if (input.contains("微信") || input.toLowerCase().contains("wechat")) {
            return "WECHAT";
        }
        if (input.contains("银行卡") || input.contains("信用卡") || input.contains("储蓄卡")
                || input.contains("借记卡") || input.contains("刷卡")) {
            return "BANK";
        }
        if (input.contains("现金") || input.contains("现钱")) {
            return "CASH";
        }
        return null;
    }

    /**
     * 别名命中且能提取金额时，直接产出草稿（不调用 AI）。
     */
    private ParseResultDTO buildFromAlias(String input, AliasResolveDTO alias, BigDecimal amount) {
        ParseResultDTO dto = new ParseResultDTO();
        DictItemDTO category = dictService.getCategory(alias.getCategoryCode());
        if (category != null) {
            dto.setCategoryCode(category.getCode());
            dto.setCategoryName(category.getName());
            dto.setBillType(Integer.valueOf(1).equals(category.getBillType()) ? 1 : 0);
        } else {
            dto.setBillType(0);
            dto.getMissingFields().add("categoryCode");
        }
        dto.setAmount(amount.setScale(2, RoundingMode.HALF_UP));

        DictItemDTO channel = alias.getChannelCode() == null ? null : dictService.getChannel(alias.getChannelCode());
        if (channel == null) {
            channel = dictService.getChannel("OTHER");
            dto.getMissingFields().add("payChannelCode");
        }
        if (channel != null) {
            dto.setPayChannelCode(channel.getCode());
            dto.setPayChannelName(channel.getName());
        }
        dto.setRecordDate(DateUtils.format(extractDate(input)));
        dto.setConfidence(new BigDecimal("0.99"));
        dto.setNeedConfirm(true);
        return dto;
    }

    /**
     * 别名对 AI 结果做覆盖（别名优先），并移除对应缺失标记。
     */
    private void applyAlias(ParseResultDTO dto, AliasResolveDTO alias) {
        if (alias.getCategoryCode() != null) {
            DictItemDTO category = dictService.getCategory(alias.getCategoryCode());
            if (category != null) {
                dto.setCategoryCode(category.getCode());
                dto.setCategoryName(category.getName());
                dto.setBillType(Integer.valueOf(1).equals(category.getBillType()) ? 1 : 0);
                dto.getMissingFields().remove("categoryCode");
            }
        }
        if (alias.getChannelCode() != null) {
            DictItemDTO channel = dictService.getChannel(alias.getChannelCode());
            if (channel != null) {
                dto.setPayChannelCode(channel.getCode());
                dto.setPayChannelName(channel.getName());
                dto.getMissingFields().remove("payChannelCode");
            }
        }
    }

    private BigDecimal extractAmount(String input) {
        Matcher matcher = AMOUNT_PATTERN.matcher(input);
        if (matcher.find()) {
            try {
                return new BigDecimal(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // 忽略，返回 0
            }
        }
        return BigDecimal.ZERO;
    }

    private LocalDate extractDate(String input) {
        if (input.contains("前天")) {
            return LocalDate.now().minusDays(2);
        }
        if (input.contains("昨天")) {
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.now();
    }
}