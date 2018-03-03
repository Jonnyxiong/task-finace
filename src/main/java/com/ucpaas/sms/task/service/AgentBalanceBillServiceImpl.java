package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.AgentBalanceBill;
import com.ucpaas.sms.task.mapper.message.AgentBalanceBillMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * @author 黄文杰
 * @description 代理商帐户余额收支明细管理
 * @date 2017-07-27
 */
@Service
public class AgentBalanceBillServiceImpl implements AgentBalanceBillService {

	private static final Logger logger = LoggerFactory.getLogger(AgentBalanceBillServiceImpl.class);

	@Autowired
	private AgentBalanceBillMapper agentBalanceBillMapper;

	@Override
	@Transactional
	public int insert(AgentBalanceBill model) {
		return this.agentBalanceBillMapper.insert(model);
	}

	@Override
	@Transactional
	public int insertBatch(List<AgentBalanceBill> modelList) {
		return this.agentBalanceBillMapper.insertBatch(modelList);
	}

	@Override
	@Transactional
	public int update(AgentBalanceBill model) {
		AgentBalanceBill old = this.agentBalanceBillMapper.getById(model.getId());
		if (old == null) {
			return 0;
		}
		return this.agentBalanceBillMapper.update(model);
	}

	@Override
	@Transactional
	public int updateSelective(AgentBalanceBill model) {
		AgentBalanceBill old = this.agentBalanceBillMapper.getById(model.getId());
		if (old != null)
			return this.agentBalanceBillMapper.updateSelective(model);
		return 0;
	}

	@Override
	@Transactional
	public AgentBalanceBill getById(Integer id) {
		AgentBalanceBill model = this.agentBalanceBillMapper.getById(id);
		return model;
	}

	@Override
	public List<AgentBalanceBill> queryAll(Map params) {
		return agentBalanceBillMapper.queryAll(params);
	}

	@Override
	@Transactional
	public int count(Map<String, Object> params) {
		return this.agentBalanceBillMapper.count(params);
	}

}
