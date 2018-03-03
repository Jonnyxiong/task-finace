package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.Account;

import java.util.List;
import java.util.Map;

/**
 * @author 黄文杰
 * @description 客户管理
 * @date 2017-07-26
 */
public interface AccountService {

	int insert(Account model);

	int insertBatch(List<Account> modelList);

	Account getByClientid(String clientid);

	int update(Account model);

	int updateSelective(Account model);

	int count(Map<String, Object> params);

}
