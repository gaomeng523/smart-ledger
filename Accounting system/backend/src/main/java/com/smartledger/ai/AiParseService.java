package com.smartledger.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.smartledger.ai.dto.DeepSeekResult;
import com.smartledger.ai.rag.RagExample;
import com.smartledger.ai.rag.RagService;
import com.smartledger.common.enums.ErrorCode;
import com.smartledger.common.exception.BizException;
import com.smartledger.common.util.DateUtils;
import com.smartledger.config.AiProperties;
import com.smartledger.config.VisionProperties;
import com.smartledger.dto.response.AliasResolveDTO;
import com.smartledger.dto.response.DictItemDTO;
import com.smartledger.dto.response.ParseResultDTO;
import com.smartledger.service.AiParseLogService;
import com.smartledger.service.AliasService;
import com.smartledger.service.DictService;
import com.smartledger.service.ImageStorageService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /** 带货币单位的金额，如「12元」「12.5 块」「¥30」 */
    private static final Pattern AMOUNT_WITH_UNIT =
            Pattern.compile("(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块|RMB|rmb|￥|¥)");

    /** 不带单位的数字，作为兜底 */
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+(?:\\.\\d{1,2})?");

    /** 日期/时间/数量等非金额数字，提取金额前先剔除，避免把「2杯」「11月12日」当成金额 */
    private static final Pattern NON_AMOUNT = Pattern.compile(
            "\\d{4}年|\\d{1,2}月\\d{1,2}[日号]|\\d{1,2}[月日号]|\\d{1,2}[:：]\\d{2}"
                    + "|\\d+(?:\\.\\d+)?\\s*(?:个月|公里|千米|分钟|小时|天|个|杯|瓶|袋|盒|包|斤|公斤|人|次|位|张|件|双|套|台)");


    /** 分类关键词词典：键为常见说法，值为 category.code；顺序即优先级（先具体后笼统）。 */
    private static final Map<String, String> CATEGORY_KEYWORDS = new LinkedHashMap<>();

    static {
        // 收入（“收红包/退款”等要先于支出的“红包/消费”）
        CATEGORY_KEYWORDS.put("工资", "SALARY_BASE");
        CATEGORY_KEYWORDS.put("薪水", "SALARY_BASE");
        CATEGORY_KEYWORDS.put("加班费", "SALARY_OVERTIME");
        CATEGORY_KEYWORDS.put("年终奖", "SALARY_ANNUAL");
        CATEGORY_KEYWORDS.put("绩效", "BONUS_PERF");
        CATEGORY_KEYWORDS.put("提成", "BONUS_COMMISSION");
        CATEGORY_KEYWORDS.put("补贴", "BONUS_ALLOWANCE");
        CATEGORY_KEYWORDS.put("利息", "INVEST_INTEREST");
        CATEGORY_KEYWORDS.put("基金", "INVEST_FUND");
        CATEGORY_KEYWORDS.put("股票", "INVEST_FUND");
        CATEGORY_KEYWORDS.put("分红", "INVEST_DIVIDEND");
        CATEGORY_KEYWORDS.put("报销", "REIMBURSE_COMPANY");
        CATEGORY_KEYWORDS.put("退款", "REIMBURSE_REFUND");
        CATEGORY_KEYWORDS.put("兼职", "INCOME_PARTTIME");
        CATEGORY_KEYWORDS.put("收红包", "INCOME_REDPACKET");

        // 餐饮
        CATEGORY_KEYWORDS.put("买菜", "FOOD_GROCERY");
        CATEGORY_KEYWORDS.put("生鲜", "FOOD_GROCERY");
        CATEGORY_KEYWORDS.put("外卖", "FOOD_TAKEOUT");
        CATEGORY_KEYWORDS.put("饿了么", "FOOD_TAKEOUT");
        CATEGORY_KEYWORDS.put("美团", "FOOD_TAKEOUT");
        CATEGORY_KEYWORDS.put("下馆子", "FOOD_DINING");
        CATEGORY_KEYWORDS.put("餐厅", "FOOD_DINING");
        CATEGORY_KEYWORDS.put("火锅", "FOOD_DINING");
        CATEGORY_KEYWORDS.put("烧烤", "FOOD_DINING");
        CATEGORY_KEYWORDS.put("咖啡", "FOOD_COFFEE");
        CATEGORY_KEYWORDS.put("奶茶", "FOOD_COFFEE");
        CATEGORY_KEYWORDS.put("星巴克", "FOOD_COFFEE");
        CATEGORY_KEYWORDS.put("瑞幸", "FOOD_COFFEE");
        CATEGORY_KEYWORDS.put("零食", "FOOD_SNACK");
        CATEGORY_KEYWORDS.put("饮料", "FOOD_SNACK");
        CATEGORY_KEYWORDS.put("水果", "FOOD_SNACK");
        CATEGORY_KEYWORDS.put("早餐", "FOOD");
        CATEGORY_KEYWORDS.put("早饭", "FOOD");
        CATEGORY_KEYWORDS.put("午餐", "FOOD");
        CATEGORY_KEYWORDS.put("午饭", "FOOD");
        CATEGORY_KEYWORDS.put("中饭", "FOOD");
        CATEGORY_KEYWORDS.put("晚餐", "FOOD");
        CATEGORY_KEYWORDS.put("晚饭", "FOOD");
        CATEGORY_KEYWORDS.put("夜宵", "FOOD");
        CATEGORY_KEYWORDS.put("吃饭", "FOOD");
        CATEGORY_KEYWORDS.put("餐饮", "FOOD");

        // 交通
        CATEGORY_KEYWORDS.put("地铁", "TRAFFIC_TRANSIT");
        CATEGORY_KEYWORDS.put("公交", "TRAFFIC_TRANSIT");
        CATEGORY_KEYWORDS.put("打车", "TRAFFIC_TAXI");
        CATEGORY_KEYWORDS.put("滴滴", "TRAFFIC_TAXI");
        CATEGORY_KEYWORDS.put("出租车", "TRAFFIC_TAXI");
        CATEGORY_KEYWORDS.put("加油", "TRAFFIC_CHARGE");
        CATEGORY_KEYWORDS.put("充电", "TRAFFIC_CHARGE");
        CATEGORY_KEYWORDS.put("停车", "TRAFFIC_PARKING");
        CATEGORY_KEYWORDS.put("过路费", "TRAFFIC_PARKING");
        CATEGORY_KEYWORDS.put("高铁", "TRAFFIC_TICKET");
        CATEGORY_KEYWORDS.put("火车票", "TRAFFIC_TICKET");
        CATEGORY_KEYWORDS.put("机票", "TRAFFIC_TICKET");

        // 购物 / 居住
        CATEGORY_KEYWORDS.put("衣服", "SHOPPING_CLOTHES");
        CATEGORY_KEYWORDS.put("鞋", "SHOPPING_CLOTHES");
        CATEGORY_KEYWORDS.put("手机", "SHOPPING_DIGITAL");
        CATEGORY_KEYWORDS.put("电脑", "SHOPPING_DIGITAL");
        CATEGORY_KEYWORDS.put("耳机", "SHOPPING_DIGITAL");
        CATEGORY_KEYWORDS.put("护肤", "SHOPPING_BEAUTY");
        CATEGORY_KEYWORDS.put("化妆品", "SHOPPING_BEAUTY");
        CATEGORY_KEYWORDS.put("理发", "SHOPPING_BEAUTY");
        CATEGORY_KEYWORDS.put("家具", "SHOPPING_HOME");
        CATEGORY_KEYWORDS.put("日用品", "SHOPPING_DAILY");
        CATEGORY_KEYWORDS.put("纸巾", "SHOPPING_DAILY");
        CATEGORY_KEYWORDS.put("超市", "SHOPPING_DAILY");
        CATEGORY_KEYWORDS.put("房租", "HOUSING_RENT");
        CATEGORY_KEYWORDS.put("租金", "HOUSING_RENT");
        CATEGORY_KEYWORDS.put("水费", "HOUSING_UTILITY");
        CATEGORY_KEYWORDS.put("电费", "HOUSING_UTILITY");
        CATEGORY_KEYWORDS.put("燃气", "HOUSING_UTILITY");
        CATEGORY_KEYWORDS.put("物业费", "HOUSING_PROPERTY");
        CATEGORY_KEYWORDS.put("话费", "HOUSING_TELECOM");
        CATEGORY_KEYWORDS.put("宽带", "HOUSING_TELECOM");

        // 娱乐 / 医疗 / 学习 / 人情
        CATEGORY_KEYWORDS.put("会员", "ENT_SUB");
        CATEGORY_KEYWORDS.put("游戏", "ENT_GAME");
        CATEGORY_KEYWORDS.put("健身", "ENT_FITNESS");
        CATEGORY_KEYWORDS.put("旅游", "ENT_TRAVEL");
        CATEGORY_KEYWORDS.put("酒店", "ENT_TRAVEL");
        CATEGORY_KEYWORDS.put("电影", "ENT_LEISURE");
        CATEGORY_KEYWORDS.put("KTV", "ENT_LEISURE");
        CATEGORY_KEYWORDS.put("挂号", "MED_CLINIC");
        CATEGORY_KEYWORDS.put("门诊", "MED_CLINIC");
        CATEGORY_KEYWORDS.put("买药", "MED_MEDICINE");
        CATEGORY_KEYWORDS.put("药店", "MED_MEDICINE");
        CATEGORY_KEYWORDS.put("药品", "MED_MEDICINE");
        CATEGORY_KEYWORDS.put("体检", "MED_CHECKUP");
        CATEGORY_KEYWORDS.put("保险", "MED_INSURANCE");
        CATEGORY_KEYWORDS.put("买书", "STUDY_BOOK");
        CATEGORY_KEYWORDS.put("书籍", "STUDY_BOOK");
        CATEGORY_KEYWORDS.put("教材", "STUDY_BOOK");
        CATEGORY_KEYWORDS.put("课程", "STUDY_COURSE");
        CATEGORY_KEYWORDS.put("培训", "STUDY_COURSE");
        CATEGORY_KEYWORDS.put("考试", "STUDY_EXAM");
        CATEGORY_KEYWORDS.put("文具", "STUDY_TOOL");
        CATEGORY_KEYWORDS.put("红包", "SOCIAL_GIFT");
        CATEGORY_KEYWORDS.put("礼金", "SOCIAL_GIFT");
        CATEGORY_KEYWORDS.put("请客", "SOCIAL_TREAT");
        CATEGORY_KEYWORDS.put("捐款", "SOCIAL_DONATE");
        CATEGORY_KEYWORDS.put("手续费", "OTHER_FEE");
        CATEGORY_KEYWORDS.put("罚款", "OTHER_FINE");
    }

    private final AiProperties aiProperties;
    private final VisionProperties visionProperties;
    private final PromptBuilder promptBuilder;
    private final DeepSeekClient deepSeekClient;
    private final VisionClient visionClient;
    private final ParseResultValidator validator;
    private final AiParseLogService aiParseLogService;
    private final AliasService aliasService;
    private final DictService dictService;
    private final ImageStorageService imageStorageService;
    private final RagService ragService;

    private Cache<String, ParseResultDTO> resultCache;
    private Cache<String, AtomicInteger> rateLimitCache;

    public AiParseService(AiProperties aiProperties,
                          VisionProperties visionProperties,
                          PromptBuilder promptBuilder,
                          DeepSeekClient deepSeekClient,
                          VisionClient visionClient,
                          ParseResultValidator validator,
                          AiParseLogService aiParseLogService,
                          AliasService aliasService,
                          DictService dictService,
                          ImageStorageService imageStorageService,
                          RagService ragService) {
        this.aiProperties = aiProperties;
        this.visionProperties = visionProperties;
        this.promptBuilder = promptBuilder;
        this.deepSeekClient = deepSeekClient;
        this.visionClient = visionClient;
        this.validator = validator;
        this.aiParseLogService = aiParseLogService;
        this.aliasService = aliasService;
        this.dictService = dictService;
        this.imageStorageService = imageStorageService;
        this.ragService = ragService;
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

        // 未启用大模型时走本地规则解析：金额 / 日期 / 渠道 / 分类关键词，零网络调用
        if (!aiProperties.isEnabled()) {
            return parseByRules(input, alias);
        }

        long start = System.currentTimeMillis();
        DeepSeekResult result;
        try {
            // RAG：按语义召回相似历史记账，作为 few-shot 注入；未开启时返回空列表，Prompt 与原来一致
            List<RagExample> examples = ragService.retrieve(input);
            result = deepSeekClient.chat(promptBuilder.buildSystemPrompt(examples), input);
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

    /**
     * 仅用本地规则解析，供离线评测做对照组（不调用模型、不写日志、不限流）。
     */
    public ParseResultDTO parseRulesOnly(String text) {
        return parseByRules(text, new AliasResolveDTO());
    }

    /**
     * 拍照识别：保存图片 → 视觉模型识别 → 校验归一，返回待确认草稿。
     *
     * <p>图片地址随草稿返回，前端保存账单时一并带上。</p>
     */
    public ParseResultDTO parseImage(String openid, MultipartFile file) {
        checkRateLimit(openid);

        ImageStorageService.StoredImage image = imageStorageService.store(file);
        String inputLog = "[图片]" + image.url();

        if (!visionProperties.isEnabled() || !StringUtils.hasText(visionProperties.getApiKey())) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "小票已保存，请补充金额与分类",
                    buildImageFallback(image.url()));
        }

        long start = System.currentTimeMillis();
        DeepSeekResult result;
        try {
            result = visionClient.chat(promptBuilder.buildReceiptPrompt(), image.bytes(), image.mimeType());
        } catch (BizException e) {
            aiParseLogService.record(openid, inputLog, null, visionProperties.getModel(), false,
                    String.valueOf(e.getCode()), e.getMessage(),
                    System.currentTimeMillis() - start, null, null);
            throw new BizException(e.getCode(), e.getMessage(), buildImageFallback(image.url()));
        }

        long cost = System.currentTimeMillis() - start;
        ParseResultDTO dto = validator.normalize(result.getRaw());
        dto.setImageUrl(image.url());
        aiParseLogService.record(openid, inputLog, result.getContent(), visionProperties.getModel(), true,
                null, null, cost, result.getPromptTokens(), result.getCompletionTokens());

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.AMOUNT_UNRECOGNIZED, ErrorCode.AMOUNT_UNRECOGNIZED.getMessage(), dto);
        }
        return dto;
    }

    /**
     * 本地规则解析（不调用任何模型）：金额、日期、支付渠道按正则/关键词提取，
     * 分类按「说法 → 分类」词典匹配；匹配不到的分类留空，交给用户在确认卡片里手选。
     */
    private ParseResultDTO parseByRules(String input, AliasResolveDTO alias) {
        ParseResultDTO dto = buildFallback(input);
        dto.setConfidence(new BigDecimal("0.70"));

        String categoryCode = matchCategoryCode(input);
        if (categoryCode != null) {
            DictItemDTO category = dictService.getCategory(categoryCode);
            if (category != null) {
                dto.setCategoryCode(category.getCode());
                dto.setCategoryName(category.getName());
                dto.setBillType(Integer.valueOf(1).equals(category.getBillType()) ? 1 : 0);
                dto.getMissingFields().remove("categoryCode");
            }
        }
        applyAlias(dto, alias);

        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.AMOUNT_UNRECOGNIZED, ErrorCode.AMOUNT_UNRECOGNIZED.getMessage(), dto);
        }
        return dto;
    }

    /**
     * 关键词 → 分类词典（先具体后笼统，命中即返回）。
     */
    private String matchCategoryCode(String input) {
        for (Map.Entry<String, String> entry : CATEGORY_KEYWORDS.entrySet()) {
            if (input.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
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
     * 图片识别不可用时的兜底草稿：图片已保存，字段全部交给用户补全。
     */
    private ParseResultDTO buildImageFallback(String imageUrl) {
        ParseResultDTO dto = new ParseResultDTO();
        dto.setBillType(0);
        dto.setAmount(BigDecimal.ZERO);
        dto.setRecordDate(DateUtils.format(LocalDate.now()));
        dto.setConfidence(BigDecimal.ZERO);
        dto.setNeedConfirm(true);
        dto.setImageUrl(imageUrl);
        dto.getMissingFields().add("amount");
        dto.getMissingFields().add("payChannelCode");
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

        // 渠道只在原文/别名明确给出时才落值；识别不到就留空，交给用户在确认卡片里选，
        // 避免默认落到「其他」让用户误以为是已识别结果。
        DictItemDTO channel = alias.getChannelCode() == null ? null : dictService.getChannel(alias.getChannelCode());
        if (channel == null) {
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
        // 1) 优先取带「元/块/¥」的数字，且取最后一个（「可乐3元 面包12.5元 合计17.5元」→ 17.5）
        Matcher unitMatcher = AMOUNT_WITH_UNIT.matcher(input);
        BigDecimal amount = null;
        while (unitMatcher.find()) {
            BigDecimal value = toDecimal(unitMatcher.group(1));
            if (value != null) {
                amount = value;
            }
        }
        if (amount != null) {
            return amount;
        }

        // 2) 没有单位时，先剔除日期/时间/数量，再取最后一个数字
        String cleaned = NON_AMOUNT.matcher(input).replaceAll(" ");
        Matcher numberMatcher = PLAIN_NUMBER.matcher(cleaned);
        while (numberMatcher.find()) {
            BigDecimal value = toDecimal(numberMatcher.group());
            if (value != null) {
                amount = value;
            }
        }
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal toDecimal(String text) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDate extractDate(String input) {
        Matcher daysAgo = Pattern.compile("(\\d{1,2})\\s*天前").matcher(input);
        if (daysAgo.find()) {
            return LocalDate.now().minusDays(Integer.parseInt(daysAgo.group(1)));
        }
        Matcher monthDay = Pattern.compile("(\\d{1,2})月(\\d{1,2})[日号]").matcher(input);
        if (monthDay.find()) {
            try {
                return LocalDate.of(LocalDate.now().getYear(),
                        Integer.parseInt(monthDay.group(1)), Integer.parseInt(monthDay.group(2)));
            } catch (Exception ignored) {
                // 非法日期（如 2月30日）回落今天
            }
        }
        if (input.contains("前天")) {
            return LocalDate.now().minusDays(2);
        }
        if (input.contains("昨天")) {
            return LocalDate.now().minusDays(1);
        }
        return LocalDate.now();
    }
}