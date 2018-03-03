package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.AccountGroup;

import java.util.List;
import java.util.Map;

/**
 * @description 客户组信息管理
 * @author 黄文杰
 * @date 2017-07-27
 */
public interface AccountGroupService {

	int insert(AccountGroup model);

	int insertBatch(List<AccountGroup> modelList);

	int update(AccountGroup model);

	int updateSelective(AccountGroup model);

	AccountGroup getByAccountGid(Integer accountGid);

	int count(Map<String, Object> params);

}
