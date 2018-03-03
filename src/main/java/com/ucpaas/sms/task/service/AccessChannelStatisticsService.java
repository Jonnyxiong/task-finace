package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.access.AccessChannelStatistics;
import com.ucpaas.sms.task.model.ResultVO;
import com.ucpaas.sms.task.model.TaskInfo;

import java.util.List;
import java.util.Map;

public interface AccessChannelStatisticsService {

	ResultVO insert(AccessChannelStatistics model);

	ResultVO insertBatch(List<AccessChannelStatistics> modelList);

	ResultVO delete(Long id);

	ResultVO update(AccessChannelStatistics model);

	ResultVO updateSelective(AccessChannelStatistics model);

	ResultVO getById(Long id);

	ResultVO count(Map<String, Object> params);

	/**
	 * 第三天的用户(客户) 运营、运维报表 如2017-02-21跑的任务，则统计计算2017-02-18的数据
	 * 
	 * @return
	 */
	boolean fourDaysAgo(TaskInfo taskInfo);

	/**
	 * 昨日的用户(客户) 运营、运维报表 如2017-02-21跑的任务，则统计计算2017-02-20的数据
	 * 
	 * @return
	 */
	boolean yesterday(TaskInfo taskInfo);

	List<AccessChannelStatistics> queryAll(Map<String, Object> params);

	int deleteByDate(String statTime);

	List<AccessChannelStatistics> queryMonthly(Map<String, Object> params);

	/**
	 * 根据客户ID和日期查询客户的统计信息，根据客户id、归属销售分组, 查询发送总数
	 * @return
	 */
	AccessChannelStatistics getTopSendBelongSaleByClientidAndDate(String clientid, Integer date);

	String  getTopSendAgentIdByClientidAndDateAndBelongSale(String clientid, Integer date, Long belongSale);

	String getTopSendOperatorstypeByClientidAndDateAndBelongSaleAndAgentId(String clientid, Integer date, Long belongSale, Integer AgentId);
}
