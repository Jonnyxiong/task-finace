package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.access.CustomerStatTemp;
import com.ucpaas.sms.task.model.ResultVO;

import java.util.List;
import java.util.Map;

public interface CustomerStatTempService {

	ResultVO insert(CustomerStatTemp model);

	ResultVO insertBatch(List<CustomerStatTemp> modelList);

	ResultVO delete(Long id);

	ResultVO update(CustomerStatTemp model);

	ResultVO updateSelective(CustomerStatTemp model);

	ResultVO getById(Long id);

	ResultVO count(Map<String, Object> params);

	/**
	 * 根据时间段，遍历所有的access表，生成CustomerStatTemp临时数据，加多代理商id分组
	 * 
	 * @param statTime
	 * @return
	 */
	List<CustomerStatTemp> generateDataIncludeAgentId(String statTime);
}
