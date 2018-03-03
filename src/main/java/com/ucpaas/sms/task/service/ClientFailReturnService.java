package com.ucpaas.sms.task.service;

import com.jsmsframework.common.dto.JsmsPage;
import com.ucpaas.sms.task.entity.access.AccessChannelStatistics;
import com.ucpaas.sms.task.entity.access.ClientFailReturn;
import com.ucpaas.sms.task.model.TaskInfo;

import java.util.List;
import java.util.Map;

/**
 * @description 客户失败返回清单表
 * @author lpjLiu
 * @date 2017-10-11
 */
public interface ClientFailReturnService {

	int insert(ClientFailReturn model);

	int insertBatch(List<ClientFailReturn> modelList);

	int update(ClientFailReturn model);

	int updateSelective(ClientFailReturn model);

	int updateBelongSale(ClientFailReturn model);

	int updateBatchBelongSale(List<ClientFailReturn> modelList);

	int updateBatchAgentId(List<ClientFailReturn> modelList);

	int updateBatchDepartmentId(List<ClientFailReturn> modelList);

	int updateBatchOperatorstype(List<ClientFailReturn> modelList);

	ClientFailReturn getById(Integer id);

	JsmsPage queryList(JsmsPage page);

	List<ClientFailReturn> findList(ClientFailReturn model);

	List<ClientFailReturn> findHasReturnGroupByDay();

	String getStartFixDay();
	String getEndFixDay();

	int count(Map<String, Object> params);

	boolean fourDaysAgo(TaskInfo taskInfo);

	boolean fixDaysAgo(TaskInfo taskInfo);

	int deleteByDate(Integer date);

	/**
	 * 查询	品牌、销售和OEM代理商预付费用户的统计列表根据天数
	 * @param date
	 * @return
	 */
	List<AccessChannelStatistics> findYuFuForClientFailReturnByDay(Integer date);

	/**
	 * 查询	OEM代理商后付费用户的统计列表根据天数
	 * @param date
	 * @return
	 */
	List<AccessChannelStatistics> findOemHouFuForClientFailReturnByDay(Integer date, List<String> ids);
}
