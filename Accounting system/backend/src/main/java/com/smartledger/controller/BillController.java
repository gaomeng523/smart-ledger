package com.smartledger.controller;

import com.smartledger.ai.AiParseService;
import com.smartledger.common.result.PageResult;
import com.smartledger.common.result.R;
import com.smartledger.common.util.UserContext;
import com.smartledger.dto.request.BillListQuery;
import com.smartledger.dto.request.BillParseRequest;
import com.smartledger.dto.request.BillSaveRequest;
import com.smartledger.dto.request.BillUpdateRequest;
import com.smartledger.dto.response.BillDTO;
import com.smartledger.dto.response.ParseResultDTO;
import com.smartledger.dto.response.SaveResultDTO;
import com.smartledger.dto.response.StatCategoryDTO;
import com.smartledger.dto.response.StatMonthDTO;
import com.smartledger.dto.response.StatTrendDTO;
import com.smartledger.dto.response.TagSuggestDTO;
import com.smartledger.service.BillService;
import com.smartledger.service.StatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账单接口：解析、保存、查询、统计。
 */
@RestController
@RequestMapping("/api/bill")
@RequiredArgsConstructor
public class BillController {

    private final AiParseService aiParseService;
    private final BillService billService;
    private final StatService statService;

    /** 自然语言解析（核心，不落库） */
    @PostMapping("/parse")
    public R<ParseResultDTO> parse(@Valid @RequestBody BillParseRequest request) {
        return R.ok(aiParseService.parse(UserContext.requireOpenid(), request.getText()));
    }

    /** 保存账单 */
    @PostMapping("/save")
    public R<SaveResultDTO> save(@Valid @RequestBody BillSaveRequest request) {
        return R.ok(billService.save(UserContext.requireOpenid(), request));
    }

    /** 账单分页列表 */
    @GetMapping("/list")
    public R<PageResult<BillDTO>> list(BillListQuery query) {
        return R.ok(billService.page(UserContext.requireOpenid(), query));
    }

    /** 历史标签联想（V2） */
    @GetMapping("/tag/suggest")
    public R<TagSuggestDTO> tagSuggest() {
        return R.ok(new TagSuggestDTO(billService.tagSuggest(UserContext.requireOpenid())));
    }

    /** 账单详情 */
    @GetMapping("/detail")
    public R<BillDTO> detail(@RequestParam Long billId) {
        return R.ok(billService.detail(UserContext.requireOpenid(), billId));
    }

    /** 修改账单 */
    @PutMapping("/update")
    public R<Void> update(@Valid @RequestBody BillUpdateRequest request) {
        billService.update(UserContext.requireOpenid(), request);
        return R.ok();
    }

    /** 删除账单（逻辑删除） */
    @DeleteMapping("/delete")
    public R<Void> delete(@RequestParam Long billId) {
        billService.delete(UserContext.requireOpenid(), billId);
        return R.ok();
    }

    /** 月度收支统计 */
    @GetMapping("/stat/month")
    public R<StatMonthDTO> statMonth(@RequestParam(required = false) String month) {
        return R.ok(statService.month(UserContext.requireOpenid(), month));
    }

    /** 分类占比统计 */
    @GetMapping("/stat/category")
    public R<StatCategoryDTO> statCategory(@RequestParam(required = false) String month,
                                          @RequestParam(required = false, defaultValue = "0") Integer billType,
                                          @RequestParam(required = false) String parentCode) {
        return R.ok(statService.category(UserContext.requireOpenid(), month, billType, parentCode));
    }

    /** 近 N 月收支趋势（V3 · F-43）：range 仅允许 6 / 12，默认 6 */
    @GetMapping("/stat/trend")
    public R<StatTrendDTO> statTrend(@RequestParam(required = false, defaultValue = "6") Integer range) {
        return R.ok(statService.trend(UserContext.requireOpenid(), range));
    }
}