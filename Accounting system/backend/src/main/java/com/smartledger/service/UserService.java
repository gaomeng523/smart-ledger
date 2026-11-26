package com.smartledger.service;

/**
 * 用户级应用服务。
 */
public interface UserService {

    /** 重置当前用户的全部记账数据（保留账号本身） */
    void reset(String openid);
}
