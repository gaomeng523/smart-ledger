# -*- coding: utf-8 -*-
"""生成智能问答（NL→SQL）评测集，可重复执行。

产物：
  src/main/resources/ai/ask-eval-seed.jsonl  50 条确定性账单（日期用「相对当前月的偏移」表示）
  src/main/resources/ai/ask-eval.jsonl       60 条中文问句 + 期望数值

设计：
  1. 种子账单只记录 monthOffset（0=本月、-1=上月、-2=上上月、-12=去年同月）与 day，
     运行期由 Java 换算成真实日期，因此任意月份跑出来的结果一致；
  2. 期望值全部由本脚本从同一份种子数据算出，口径与 SQL 一致：
     金额 SUM(amount)、笔数 COUNT(*)、平均值四舍五入到分；
  3. 问句覆盖：月度收支/结余/笔数、一级与二级分类、支付渠道、环比差值、
     近三个月、最大单笔、平均值、去年同月、分类+渠道组合、排除类问法。
"""
import json
import os
from decimal import Decimal, ROUND_HALF_UP

OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "..", "src", "main", "resources", "ai")

# (monthOffset, day, amount, billType, categoryCode, payChannelCode, remark)
SEED = [
    # 本月
    (0, 3, "45.50", 0, "FOOD_GROCERY", "WECHAT", "超市买菜"),
    (0, 5, "28.00", 0, "FOOD_TAKEOUT", "WECHAT", "点外卖"),
    (0, 8, "180.00", 0, "FOOD_DINING", "ALIPAY", "下馆子"),
    (0, 9, "19.00", 0, "FOOD_COFFEE", "WECHAT", "咖啡"),
    (0, 2, "4.00", 0, "TRAFFIC_TRANSIT", "WECHAT", "坐地铁"),
    (0, 11, "32.00", 0, "TRAFFIC_TAXI", "ALIPAY", "打车"),
    (0, 12, "599.00", 0, "SHOPPING_CLOTHES", "ALIPAY", "买衣服"),
    (0, 1, "3200.00", 0, "HOUSING_RENT", "BANK", "交房租"),
    (0, 6, "120.00", 0, "HOUSING_UTILITY", "WECHAT", "交电费"),
    (0, 15, "45.00", 0, "ENT_LEISURE", "WECHAT", "看电影"),
    (0, 18, "35.00", 0, "MED_MEDICINE", "CASH", "买药"),
    (0, 20, "59.00", 0, "STUDY_BOOK", "ALIPAY", "买书"),
    (0, 10, "12000.00", 1, "SALARY_BASE", "BANK", "工资"),
    (0, 20, "120.00", 1, "INVEST_INTEREST", "BANK", "利息"),
    # 上月
    (-1, 4, "62.00", 0, "FOOD_GROCERY", "WECHAT", "超市买菜"),
    (-1, 14, "240.00", 0, "FOOD_DINING", "ALIPAY", "下馆子"),
    (-1, 7, "42.50", 0, "FOOD_TAKEOUT", "WECHAT", "点外卖"),
    (-1, 10, "45.50", 0, "TRAFFIC_TAXI", "ALIPAY", "打车"),
    (-1, 18, "899.00", 0, "SHOPPING_DIGITAL", "ALIPAY", "买耳机"),
    (-1, 1, "3200.00", 0, "HOUSING_RENT", "BANK", "交房租"),
    (-1, 22, "480.00", 0, "ENT_TRAVEL", "WECHAT", "酒店住宿"),
    (-1, 10, "12000.00", 1, "SALARY_BASE", "BANK", "工资"),
    # 上上月
    (-2, 6, "38.90", 0, "FOOD_GROCERY", "WECHAT", "超市买菜"),
    (-2, 8, "25.00", 0, "FOOD_COFFEE", "WECHAT", "咖啡"),
    (-2, 3, "6.00", 0, "TRAFFIC_TRANSIT", "WECHAT", "坐地铁"),
    (-2, 1, "3200.00", 0, "HOUSING_RENT", "BANK", "交房租"),
    (-2, 9, "320.00", 0, "MED_CLINIC", "ALIPAY", "门诊挂号"),
    (-2, 10, "9500.00", 1, "SALARY_BASE", "BANK", "工资"),
    # 去年同月
    (-12, 5, "23.00", 0, "FOOD_GROCERY", "WECHAT", "超市买菜"),
    (-12, 16, "96.00", 0, "FOOD_DINING", "ALIPAY", "下馆子"),
    (-12, 1, "2500.00", 0, "HOUSING_RENT", "BANK", "交房租"),
    (-12, 10, "9500.00", 1, "SALARY_BASE", "BANK", "工资"),
]

RECENT = (0, -1, -2)


def _pick(offset, prefix=None, code=None, channel=None, bill_type=None):
    result = []
    for row in SEED:
        if offset is not None and row[0] != offset:
            continue
        if prefix is not None and not row[4].startswith(prefix):
            continue
        if code is not None and row[4] != code:
            continue
        if channel is not None and row[5] != channel:
            continue
        if bill_type is not None and row[3] != bill_type:
            continue
        result.append(row)
    return result


def money(offset=None, prefix=None, code=None, channel=None, bill_type=0, offsets=None):
    """金额合计，默认只算支出（bill_type=0）。"""
    rows = []
    if offsets is not None:
        for item in offsets:
            rows.extend(_pick(item, prefix, code, channel, bill_type))
    else:
        rows = _pick(offset, prefix, code, channel, bill_type)
    return sum((Decimal(row[2]) for row in rows), Decimal("0"))


def cnt(offset=None, prefix=None, code=None, channel=None, bill_type=None, offsets=None):
    """账单笔数，默认不限收支类型。"""
    if offsets is not None:
        return sum(len(_pick(item, prefix, code, channel, bill_type)) for item in offsets)
    return len(_pick(offset, prefix, code, channel, bill_type))


def avg(offset):
    return (money(offset, bill_type=0) / Decimal(cnt(offset, bill_type=0))).quantize(
        Decimal("0.01"), rounding=ROUND_HALF_UP)


def num(value):
    """Decimal → JSON 数值（整数输出 int，避免 14.00 这种噪声）。"""
    value = Decimal(value)
    if value == value.to_integral_value():
        return int(value)
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


month_exp_0, month_exp_1, month_exp_2 = money(0), money(-1), money(-2)
month_inc_0, month_inc_1, month_inc_2 = money(0, bill_type=1), money(-1, bill_type=1), money(-2, bill_type=1)

QUESTIONS = [
    # 月度总览：支出 / 收入 / 结余 / 笔数
    ("这个月我一共花了多少钱？", month_exp_0),
    ("这个月收入是多少？", month_inc_0),
    ("这个月结余多少？", month_inc_0 - month_exp_0),
    ("这个月记了多少笔账？", cnt(0)),
    ("上个月总支出是多少？", month_exp_1),
    ("上个月收入多少？", month_inc_1),
    ("上个月结余是多少？", month_inc_1 - month_exp_1),
    ("上个月一共记了几笔？", cnt(-1)),
    ("上上个月花了多少钱？", month_exp_2),
    ("上上个月收入多少？", month_inc_2),
    ("上上个月结余多少？", month_inc_2 - month_exp_2),
    ("上上个月有几笔账单？", cnt(-2)),
    # 一级分类
    ("这个月吃饭花了多少？", money(0, prefix="FOOD")),
    ("这个月交通花了多少？", money(0, prefix="TRAFFIC")),
    ("这个月购物花了多少？", money(0, prefix="SHOPPING")),
    ("这个月房租水电花了多少？", money(0, prefix="HOUSING")),
    ("上个月吃饭花了多少钱？", money(-1, prefix="FOOD")),
    ("上上个月吃饭花了多少？", money(-2, prefix="FOOD")),
    # 二级分类
    ("这个月点外卖花了多少钱？", money(0, code="FOOD_TAKEOUT")),
    ("这个月房租多少钱？", money(0, code="HOUSING_RENT")),
    ("上个月买数码产品花了多少？", money(-1, code="SHOPPING_DIGITAL")),
    ("这个月打车花了多少？", money(0, code="TRAFFIC_TAXI")),
    ("上个月打车花了多少钱？", money(-1, code="TRAFFIC_TAXI")),
    ("上上个月门诊看病花了多少？", money(-2, code="MED_CLINIC")),
    # 支付渠道
    ("这个月微信支付花了多少钱？", money(0, channel="WECHAT")),
    ("这个月支付宝花了多少钱？", money(0, channel="ALIPAY")),
    ("上个月支付宝花了多少？", money(-1, channel="ALIPAY")),
    ("上个月微信花了多少钱？", money(-1, channel="WECHAT")),
    ("这个月银行卡收到多少钱？", money(0, channel="BANK", bill_type=1)),
    ("这个月用现金花了多少？", money(0, channel="CASH")),
    # 环比差值
    ("这个月比上个月少花多少钱？", month_exp_1 - month_exp_0),
    ("上个月比上上个月多花多少钱？", month_exp_1 - month_exp_2),
    ("这个月收入比上个月多了多少？", month_inc_0 - month_inc_1),
    ("这个月结余比上个月多了多少？", (month_inc_0 - month_exp_0) - (month_inc_1 - month_exp_1)),
    # 近三个月
    ("最近三个月一共花了多少钱？", money(offsets=RECENT)),
    ("最近三个月收入一共多少？", money(offsets=RECENT, bill_type=1)),
    ("最近三个月一共记了多少笔？", cnt(offsets=RECENT)),
    ("最近三个月吃饭一共花了多少？", money(offsets=RECENT, prefix="FOOD")),
    # 最大单笔 / 平均值
    ("这个月最大的一笔支出是多少？", max(Decimal(r[2]) for r in _pick(0, bill_type=0))),
    ("上个月最大的一笔支出多少钱？", max(Decimal(r[2]) for r in _pick(-1, bill_type=0))),
    ("这个月平均每笔支出多少钱？", avg(0)),
    ("上个月平均每笔支出多少？", avg(-1)),
    # 去年同月
    ("去年这个月一共花了多少钱？", money(-12)),
    ("这个月比去年同月多花了多少？", month_exp_0 - money(-12)),
    ("去年这个月收入多少？", money(-12, bill_type=1)),
    ("去年这个月记了几笔账？", cnt(-12)),
    # 分类 + 渠道组合
    ("这个月用支付宝吃饭花了多少？", money(0, prefix="FOOD", channel="ALIPAY")),
    ("这个月用微信吃饭花了多少？", money(0, prefix="FOOD", channel="WECHAT")),
    ("上个月用微信吃饭花了多少？", money(-1, prefix="FOOD", channel="WECHAT")),
    ("上个月用支付宝购物花了多少钱？", money(-1, prefix="SHOPPING", channel="ALIPAY")),
    # 笔数
    ("这个月吃饭记了几笔？", cnt(0, prefix="FOOD")),
    ("上个月吃饭有几笔？", cnt(-1, prefix="FOOD")),
    ("这个月用微信支付记了几笔？", cnt(0, channel="WECHAT")),
    # 排除 / 其他口径
    ("这个月除了吃饭还花了多少钱？", month_exp_0 - money(0, prefix="FOOD")),
    ("这个月除了房租花了多少钱？", month_exp_0 - money(0, code="HOUSING_RENT")),
    ("这个月娱乐花了多少钱？", money(0, prefix="ENT")),
    ("上个月旅游花了多少？", money(-1, code="ENT_TRAVEL")),
    ("上上个月医疗花了多少？", money(-2, prefix="MED")),
    ("这个月最大的一笔收入是多少？", max(Decimal(r[2]) for r in _pick(0, bill_type=1))),
    ("这个月除了吃饭和住房，其他花销是多少？",
     month_exp_0 - money(0, prefix="FOOD") - money(0, prefix="HOUSING")),
]


def main():
    assert len(QUESTIONS) == 60, "评测集应为 60 条，当前 %d 条" % len(QUESTIONS)
    assert len(SEED) == 32, "种子账单应为 32 条，当前 %d 条" % len(SEED)

    seed_path = os.path.join(OUT_DIR, "ask-eval-seed.jsonl")
    with open(seed_path, "w", encoding="utf-8") as f:
        for index, row in enumerate(SEED):
            item = {
                "monthOffset": row[0],
                "day": row[1],
                "amount": str(row[2]),
                "billType": row[3],
                "categoryCode": row[4],
                "payChannelCode": row[5],
                "remark": row[6],
                "requestId": "ask-eval-%02d" % index,
            }
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    ask_path = os.path.join(OUT_DIR, "ask-eval.jsonl")
    with open(ask_path, "w", encoding="utf-8") as f:
        for question, expect in QUESTIONS:
            f.write(json.dumps({"question": question, "expect": num(expect)}, ensure_ascii=False) + "\n")

    print("seed:", seed_path, len(SEED))
    print("questions:", ask_path, len(QUESTIONS))


if __name__ == "__main__":
    main()
