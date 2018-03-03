package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.AgentBalanceBill;

import java.util.List;
import java.util.Map;

/**
 * @description 客户组信息管理
 * @author 黄文杰
 * @date 2017-07-27
 */
public interface AgentInfoService {

	List<String> findOemAgentIdList();

	String getUserPriceByClientId(Map params);

	int addAgentAccountBalance(Map params);

	String getAgentBalanceByAgentId(String agentId);

	int addAgentBalanceBill(AgentBalanceBill agentBalanceBill);
}
