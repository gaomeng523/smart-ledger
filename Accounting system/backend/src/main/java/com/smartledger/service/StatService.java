package com.smartledger.service;

import com.smartledger.dto.response.StatCategoryDTO;
import com.smartledger.dto.response.StatMonthDTO;
import com.smartledger.dto.response.StatTrendDTO;

/**
 * 统计服务。
 */
public interface StatService {

    /** 月度收支统计 */
    StatMonthDTO month(String openid, String month);

    /** 分类占比统计：parentCode 为空时按一级汇总，传入一级 code 时下钻该类的二级明细 */
    StatCategoryDTO category(String openid, String month, Integer billType, String parentCode);

    /** 近 N 月收支趋势（含环比、当月同比，V3 · F-43） */
    StatTrendDTO trend(String openid, Integer range);
}