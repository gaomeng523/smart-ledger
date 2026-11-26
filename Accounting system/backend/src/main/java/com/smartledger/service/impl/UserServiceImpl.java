package com.smartledger.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartledger.entity.AiParseLog;
import com.smartledger.entity.AliasDict;
import com.smartledger.entity.Bill;
import com.smartledger.entity.BillTemplate;
import com.smartledger.entity.Budget;
import com.smartledger.entity.ImportRecord;
import com.smartledger.entity.SavingsGoal;
import com.smartledger.entity.Subscription;
import com.smartledger.mapper.AiParseLogMapper;
import com.smartledger.mapper.AliasDictMapper;
import com.smartledger.mapper.BillMapper;
import com.smartledger.mapper.BillTemplateMapper;
import com.smartledger.mapper.BudgetMapper;
import com.smartledger.mapper.ImportRecordMapper;
import com.smartledger.mapper.SavingsGoalMapper;
import com.smartledger.mapper.SubscriptionMapper;
import com.smartledger.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户数据服务实现：按 openid 清空记账数据，账号、分类与支付渠道字典不受影响。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final BillMapper billMapper;
    private final BudgetMapper budgetMapper;
    private final BillTemplateMapper billTemplateMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final SavingsGoalMapper savingsGoalMapper;
    private final AliasDictMapper aliasDictMapper;
    private final ImportRecordMapper importRecordMapper;
    private final AiParseLogMapper aiParseLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reset(String openid) {
        // 带逻辑删除字段的表（bill / subscription / savings_goal）会被置为 deleted=1
        billMapper.delete(new LambdaQueryWrapper<Bill>().eq(Bill::getOpenid, openid));
        subscriptionMapper.delete(new LambdaQueryWrapper<Subscription>().eq(Subscription::getOpenid, openid));
        savingsGoalMapper.delete(new LambdaQueryWrapper<SavingsGoal>().eq(SavingsGoal::getOpenid, openid));
        // 配置类与日志类数据直接物理删除
        budgetMapper.delete(new LambdaQueryWrapper<Budget>().eq(Budget::getOpenid, openid));
        billTemplateMapper.delete(new LambdaQueryWrapper<BillTemplate>().eq(BillTemplate::getOpenid, openid));
        aliasDictMapper.delete(new LambdaQueryWrapper<AliasDict>().eq(AliasDict::getOpenid, openid));
        importRecordMapper.delete(new LambdaQueryWrapper<ImportRecord>().eq(ImportRecord::getOpenid, openid));
        aiParseLogMapper.delete(new LambdaQueryWrapper<AiParseLog>().eq(AiParseLog::getOpenid, openid));
    }
}
