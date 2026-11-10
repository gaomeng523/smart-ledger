package com.smartledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartledger.entity.Bill;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账单 Mapper。
 */
public interface BillMapper extends BaseMapper<Bill> {

    /**
     * 月度收支汇总。统计口径：record_date 落在 [start, end)，仅统计未删除记录。
     */
    @Select("SELECT "
            + "COALESCE(SUM(CASE WHEN bill_type = 1 THEN amount ELSE 0 END), 0) AS income, "
            + "COALESCE(SUM(CASE WHEN bill_type = 0 THEN amount ELSE 0 END), 0) AS expense, "
            + "COUNT(*) AS billCount "
            + "FROM bill "
            + "WHERE openid = #{openid} AND deleted = 0 "
            + "AND record_date >= #{start} AND record_date < #{end}")
    Map<String, Object> selectMonthStat(@Param("openid") String openid,
                                        @Param("start") LocalDate start,
                                        @Param("end") LocalDate end);

    /**
     * 按分类聚合（分类占比统计）。返回 categoryCode / amount 两列。
     */
    @Select("SELECT category_code AS categoryCode, COALESCE(SUM(amount), 0) AS amount "
            + "FROM bill "
            + "WHERE openid = #{openid} AND deleted = 0 AND bill_type = #{billType} "
            + "AND record_date >= #{start} AND record_date < #{end} "
            + "GROUP BY category_code "
            + "ORDER BY amount DESC")
    List<Map<String, Object>> selectCategoryStat(@Param("openid") String openid,
                                                 @Param("billType") Integer billType,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end);

    /**
     * 按月聚合收支（V3 趋势报表）。统计口径：record_date 落在 [start, end)，仅统计未删除记录。
     * 返回 month(yyyy-MM) / income / expense 三列，无数据的月份不在结果中（由应用层补零）。
     */
    @Select("SELECT DATE_FORMAT(record_date, '%Y-%m') AS month, "
            + "COALESCE(SUM(CASE WHEN bill_type = 1 THEN amount ELSE 0 END), 0) AS income, "
            + "COALESCE(SUM(CASE WHEN bill_type = 0 THEN amount ELSE 0 END), 0) AS expense "
            + "FROM bill "
            + "WHERE openid = #{openid} AND deleted = 0 "
            + "AND record_date >= #{start} AND record_date < #{end} "
            + "GROUP BY DATE_FORMAT(record_date, '%Y-%m')")
    List<Map<String, Object>> selectMonthlyTrend(@Param("openid") String openid,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end);

    /**
     * 取用户近期账单的标签存储串（逗号分隔），供标签联想在应用层聚合频次（V2）。
     */
    @Select("SELECT tags FROM bill "
            + "WHERE openid = #{openid} AND deleted = 0 AND tags IS NOT NULL AND tags <> '' "
            + "ORDER BY id DESC LIMIT 1000")
    List<String> selectTagStrings(@Param("openid") String openid);
}