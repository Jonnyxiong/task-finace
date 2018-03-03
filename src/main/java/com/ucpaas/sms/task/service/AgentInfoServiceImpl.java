package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.AgentBalanceBill;
import com.ucpaas.sms.task.mapper.message.AgentInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * @description 客户组信息管理
 * @author 黄文杰
 * @date 2017-07-27
 */
@Service
public class AgentInfoServiceImpl implements AgentInfoService {

	@Autowired
	private AgentInfoMapper agentInfoMapper;

	@Override
	public List<String> findOemAgentIdList() {
		return agentInfoMapper.findOemAgentIdList();
	}

	@Override
	public String getUserPriceByClientId(Map params) {
		return agentInfoMapper.getUserPriceByClientId(params);
	}


	@Override
	@Transactional("message")
	public int addAgentAccountBalance(Map params) {
		return agentInfoMapper.addAgentAccountBalance(params);
	}

	@Override
	public String getAgentBalanceByAgentId(String agentId) {
		return agentInfoMapper.getAgentBalanceByAgentId(agentId);
	}

	@Override
	@Transactional("message")
	public int addAgentBalanceBill(AgentBalanceBill agentBalanceBill) {
		return agentInfoMapper.addAgentBalanceBill(agentBalanceBill);
	}
}
