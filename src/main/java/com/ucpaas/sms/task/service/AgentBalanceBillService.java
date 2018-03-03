package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.AgentBalanceBill;

import java.util.List;
import java.util.Map;

/**
 * @description 代理商帐户余额收支明细管理
 * @author 黄文杰
 * @date 2017-07-27
 */
public interface AgentBalanceBillService {

	int insert(AgentBalanceBill model);

	int insertBatch(List<AgentBalanceBill> modelList);

	int update(AgentBalanceBill model);

	int updateSelective(AgentBalanceBill model);

	AgentBalanceBill getById(Integer id);

	List<AgentBalanceBill> queryAll(Map params);

	int count(Map<String, Object> params);

}
