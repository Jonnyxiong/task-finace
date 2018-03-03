package com.ucpaas.sms.task.service;

import com.jsmsframework.common.dto.JsmsPage;
import com.ucpaas.sms.common.util.DateUtils;
import com.ucpaas.sms.task.entity.access.AccessChannelStatistics;
import com.ucpaas.sms.task.entity.access.ClientFailReturn;
import com.ucpaas.sms.task.mapper.access.AccessChannelStatisticsMapper;
import com.ucpaas.sms.task.mapper.access.ClientFailReturnMapper;
import com.ucpaas.sms.task.mapper.message.AgentInfoMapper;
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
 * @description 客户失败返回清单表
 * @author lpjLiu
 * @date 2017-10-11
 */
@Service
public class ClientFailReturnServiceImpl implements ClientFailReturnService {

	private static final Logger logger = LoggerFactory.getLogger("ClientFailReturnService");

	@Autowired
	private ClientFailReturnMapper clientFailReturnMapper;

	@Autowired
	private AgentInfoMapper agentInfoMapper;

	@Autowired
	private AccessChannelStatisticsMapper accessChannelStatisticsMapper;

	@Autowired
	@Qualifier("deman2point3AccessStatistic")
	private AccessStatisticStrategy accessStatisticStrategy;

	@Resource(name = "access_transactionManager_new")
	private DataSourceTransactionManager txManager;

	@Override
	@Transactional(value = "access")
	public int insert(ClientFailReturn model) {
		return this.clientFailReturnMapper.insert(model);
	}

	@Override
	@Transactional(value = "access")
	public int insertBatch(List<ClientFailReturn> modelList) {
		return this.clientFailReturnMapper.insertBatch(modelList);
	}

	@Override
	@Transactional(value = "access")
	public int update(ClientFailReturn model) {
		ClientFailReturn old = this.clientFailReturnMapper.getById(model.getId());
		if (old == null) {
			return 0;
		}
		return this.clientFailReturnMapper.update(model);
	}

	@Override
	@Transactional(value = "access")
	public int updateSelective(ClientFailReturn model) {
		ClientFailReturn old = this.clientFailReturnMapper.getById(model.getId());
		if (old != null)
			return this.clientFailReturnMapper.updateSelective(model);
		return 0;
	}

	@Override
	@Transactional(value = "access")
	public int updateBelongSale(ClientFailReturn model) {
		return clientFailReturnMapper.updateBelongSale(model);
	}

	@Override
	@Transactional(value = "access")
	public int updateBatchBelongSale(List<ClientFailReturn> modelList) {
		return clientFailReturnMapper.updateBatchBelongSale(modelList);
	}

	@Override
	@Transactional(value = "access")
	public int updateBatchAgentId(List<ClientFailReturn> modelList) {
		return clientFailReturnMapper.updateBatchAgentId(modelList);
	}

	@Override
	@Transactional(value = "access")
	public int updateBatchDepartmentId(List<ClientFailReturn> modelList) {
		return clientFailReturnMapper.updateBatchDepartmentId(modelList);
	}

	@Override
	@Transactional(value = "access")
	public int updateBatchOperatorstype(List<ClientFailReturn> modelList) {
		return clientFailReturnMapper.updateBatchOperatorstype(modelList);
	}

	@Override
	@Transactional(value = "access")
	public ClientFailReturn getById(Integer id) {
		ClientFailReturn model = this.clientFailReturnMapper.getById(id);
		return model;
	}

	@Override
	public JsmsPage queryList(JsmsPage page) {
		List<ClientFailReturn> list = this.clientFailReturnMapper.queryList(page);
		page.setData(list);
		return page;
	}

	@Override
	public List<ClientFailReturn> findList(ClientFailReturn model) {
		return this.clientFailReturnMapper.findList(model);
	}

	@Override
	public List<ClientFailReturn> findHasReturnGroupByDay() {
		return this.clientFailReturnMapper.findHasReturnGroupByDay();
	}

	@Override
	public String getStartFixDay() {
		return this.clientFailReturnMapper.getStartFixDay();
	}

	@Override
	public String getEndFixDay() {
		return this.clientFailReturnMapper.getEndFixDay();
	}

	@Override
	public int count(Map<String, Object> params) {
		return this.clientFailReturnMapper.count(params);
	}

	@Override
	@Transactional(value = "access")
	public int deleteByDate(Integer date) {
		return this.clientFailReturnMapper.deleteByDate(date);
	}

	@Override
	public List<AccessChannelStatistics> findYuFuForClientFailReturnByDay(Integer date) {
		return accessChannelStatisticsMapper.findYuFuForClientFailReturnByDay(date);
	}

	@Override
	public List<AccessChannelStatistics> findOemHouFuForClientFailReturnByDay(Integer date, List<String> ids) {
		return accessChannelStatisticsMapper.findOemHouFuForClientFailReturnByDay(date, ids);
	}

	@Override
	public boolean fourDaysAgo(TaskInfo taskInfo) {
		String format = taskInfo.getExecuteType().getFormat();
		DateTime executeNext = UcpaasDateUtils.parseDate(taskInfo.getExecuteNext(), format);
		DateTime statDay = executeNext.minusDays(4);
		logger.debug("第前四天的客户失败返返清单表任务【开始】：统计日期 = {} ------------------", statDay.toString("yyyyMMdd"));
		long beginTime = System.currentTimeMillis();

		// 检查access统计是否完成
		DateTime finishDate = executeNext.plusDays(1);
		int accessChannelTask = agentInfoMapper
				.checkAccessChannelFourdayStatDone(DateUtils.formatDate(finishDate.toDate(), "yyyyMMdd"));
		if (accessChannelTask == 0) {
			logger.debug("第前四天的客户失败返返清单表任务【暂停】：每日统计ACCESS报表（第4天前的数据）任务正在运行或还未运行，须等待其完成...");
			return false;
		}

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setName("SomeTxName");
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

		TransactionStatus status = txManager.getTransaction(def);
		try {
			accessStatisticStrategy.staticClientFailReturn(statDay);
		} catch (Exception e) {
			logger.error("第前四天的客户失败返还清单统计失败(进行回滚 )", e);
			txManager.rollback(status);
			logger.error("回滚成功 ");
			return false;
		}
		txManager.commit(status);
		logger.debug("第前四天的客户失败返返清单表任务【结束】：耗时 = {}", System.currentTimeMillis() - beginTime);
		return true;
	}

	@Override
	public boolean fixDaysAgo(TaskInfo taskInfo) {
		String format = taskInfo.getExecuteType().getFormat();
		DateTime executeNext = UcpaasDateUtils.parseDate(taskInfo.getExecuteNext(), format);
		DateTime statDay = executeNext.minusDays(1);
		logger.debug("修复客户失败返返清单表任务【开始】：统计日期 = {} ------------------", statDay.toString("yyyyMMdd"));
		long beginTime = System.currentTimeMillis();

		DefaultTransactionDefinition def = new DefaultTransactionDefinition();
		def.setName("SomeTxName");
		def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

		TransactionStatus status = txManager.getTransaction(def);
		try {
			accessStatisticStrategy.fixStaticClientFailReturn(statDay);
		} catch (Exception e) {
			logger.error("修复客户失败返返清单表任务失败(进行回滚 )", e);
			txManager.rollback(status);
			logger.error("回滚成功 ");
			return false;
		}
		txManager.commit(status);
		logger.debug("修复客户失败返返清单表任务【结束】：耗时 = {}", System.currentTimeMillis() - beginTime);
		return true;
	}
}
