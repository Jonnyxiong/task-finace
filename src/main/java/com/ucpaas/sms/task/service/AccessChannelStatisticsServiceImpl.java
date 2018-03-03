package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.access.AccessChannelStatistics;
import com.ucpaas.sms.task.mapper.access.AccessChannelStatisticsMapper;
import com.ucpaas.sms.task.model.ResultVO;
import com.ucpaas.sms.task.model.TaskInfo;
import com.ucpaas.sms.task.statistic.AccessStatisticStrategy;
import com.ucpaas.sms.task.util.UcpaasDateUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author huangwenjie
 * @title
 * @description 客户(用户)运营、运维报表统计
 * @date 2017-02-21
 */
@Service
public class AccessChannelStatisticsServiceImpl implements AccessChannelStatisticsService {
	private static final Logger LOGGER = LoggerFactory.getLogger("AccessChannelStatisticsService");

	@Autowired
	private AccessChannelStatisticsMapper accessChannelStatisticsMapper;

	@Autowired
	@Qualifier("deman2point3AccessStatistic")
	private AccessStatisticStrategy accessStatisticStrategy;

	@Resource(name = "access_transactionManager_new")
	private DataSourceTransactionManager txManager;

	@Override
	@Transactional(value = "access")
	public ResultVO insert(AccessChannelStatistics model) {
		this.accessChannelStatisticsMapper.insert(model);
		return ResultVO.successDefault();
	}

	@Override
	@Transactional(value = "access")
	public ResultVO insertBatch(List<AccessChannelStatistics> modelList) {
		this.accessChannelStatisticsMapper.insertBatch(modelList);
		return ResultVO.successDefault();
	}

	@Override
	@Transactional(value = "access")
	public ResultVO delete(Long id) {
		AccessChannelStatistics model = this.accessChannelStatisticsMapper.getById(id);
		if (model != null)
			this.accessChannelStatisticsMapper.delete(id);
		return ResultVO.successDefault();
	}

	@Override
	@Transactional(value = "access")
	public ResultVO update(AccessChannelStatistics model) {
		AccessChannelStatistics old = this.accessChannelStatisticsMapper.getById(model.getId());
		if (old == null) {
			return ResultVO.failure();
		}
		this.accessChannelStatisticsMapper.update(model);
		AccessChannelStatistics newModel = this.accessChannelStatisticsMapper.getById(model.getId());
		return ResultVO.successDefault(newModel);
	}

	@Override
	@Transactional(value = "access")
	public ResultVO updateSelective(AccessChannelStatistics model) {
		AccessChannelStatistics old = this.accessChannelStatisticsMapper.getById(model.getId());
		if (old != null)
			this.accessChannelStatisticsMapper.updateSelective(model);
		return ResultVO.successDefault();
	}

	@Override
	@Transactional(value = "access")
	public ResultVO getById(Long id) {
		AccessChannelStatistics model = this.accessChannelStatisticsMapper.getById(id);
		return ResultVO.successDefault(model);
	}

	@Override
	@Transactional(value = "access")
	public ResultVO count(Map<String, Object> params) {
		return ResultVO.successDefault(this.accessChannelStatisticsMapper.count(params));
	}

	@Override
	@Transactional(value = "access")
	public List<AccessChannelStatistics> queryAll(Map<String, Object> params) {
		return this.accessChannelStatisticsMapper.queryAll(params);
	}

	@Override
	@Transactional(value = "access")
	public int deleteByDate(String statTime) {
		return accessChannelStatisticsMapper.deleteByDate(statTime);
	}

	@Override
	public List<AccessChannelStatistics> queryMonthly(Map<String, Object> params) {
		return accessChannelStatisticsMapper.queryMonthly(params);
	}

	@Override
	public AccessChannelStatistics getTopSendBelongSaleByClientidAndDate(String clientid, Integer date) {
		return accessChannelStatisticsMapper.getTopSendBelongSaleByClientidAndDate(clientid, date);
	}

	@Override
	public String getTopSendAgentIdByClientidAndDateAndBelongSale(String clientid, Integer date, Long belongSale) {
		return accessChannelStatisticsMapper.getTopSendAgentIdByClientidAndDateAndBelongSale(clientid, date, belongSale);
	}

	@Override
	public String getTopSendOperatorstypeByClientidAndDateAndBelongSaleAndAgentId(String clientid, Integer date, Long belongSale, Integer AgentId) {
		return accessChannelStatisticsMapper.getTopSendOperatorstypeByClientidAndDateAndBelongSaleAndAgentId(clientid, date, belongSale, AgentId);
	}

	@Override
	public boolean fourDaysAgo(TaskInfo taskInfo) {
		String format = taskInfo.getExecuteType().getFormat();
		DateTime executeNext = UcpaasDateUtils.parseDate(taskInfo.getExecuteNext(), format);
		DateTime statDay = executeNext.minusDays(4);
		LOGGER.debug("第前四天的客户运营运维统计报表任务【开始】：统计日期 = {} ------------------", statDay.toString("yyyyMMdd"));
		long begainTime = System.currentTimeMillis();
		// boolean result = false;
		// result = generateDataIn(statDay);

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		// explicitly setting the transaction name is something that can only be
		// done programmatically
		def.setName("SomeTxName");
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

		TransactionStatus status = txManager.getTransaction(def);
		try {
			accessStatisticStrategy.staticAlgorithm(statDay);
		} catch (Exception e) {
			LOGGER.error("第前四天的用户侧统计失败(进行回滚 )", e);
			txManager.rollback(status);
			LOGGER.error("回滚成功 ");
			return false;
		}
		txManager.commit(status);
		LOGGER.debug("第前四天的客户运营运维统计报表任务【结束】：耗时 = {}", System.currentTimeMillis() - begainTime);
		return true;
	}

	@Override
	public boolean yesterday(TaskInfo taskInfo) {
		String format = taskInfo.getExecuteType().getFormat();
		DateTime executeNext = UcpaasDateUtils.parseDate(taskInfo.getExecuteNext(), format);
		DateTime statDay = executeNext.minusDays(1);
		LOGGER.debug("第前一天(昨天)的客户运营运维统计报表任务【开始】：统计日期 = {} ------------------", statDay.toString("yyyyMMdd"));
		long begainTime = System.currentTimeMillis();
		// boolean result = false;
		// result = generateDataIn(statDay);
		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		// explicitly setting the transaction name is something that can only be
		// done programmatically
		def.setName("SomeTxName");
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

		TransactionStatus status = txManager.getTransaction(def);
		try {
			accessStatisticStrategy.staticAlgorithm(statDay);
		} catch (Exception e) {
			LOGGER.error("第前一天的用户侧统计失败", e);
			txManager.rollback(status);
			LOGGER.error("回滚成功 ");
			return false;
		}
		txManager.commit(status);
		LOGGER.debug("第前一天(昨天)的客户运营运维统计报表任务【结束】：耗时 = {}", System.currentTimeMillis() - begainTime);
		return true;
	}

}
