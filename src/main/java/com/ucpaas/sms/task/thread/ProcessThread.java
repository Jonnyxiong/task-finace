package com.ucpaas.sms.task.thread;

import com.ucpaas.sms.task.constant.TaskConstant.ExecuteType;
import com.ucpaas.sms.task.constant.TaskConstant.TaskStatus;
import com.ucpaas.sms.task.model.TaskInfo;
import com.ucpaas.sms.task.service.AccessChannelStatisticsService;
import com.ucpaas.sms.task.service.ClientFailReturnService;
import com.ucpaas.sms.task.service.OemAgentClientChargeService;
import com.ucpaas.sms.task.service.common.TaskRunService;
import com.ucpaas.sms.task.task.TableTask;
import com.ucpaas.sms.task.util.TaskUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import java.util.List;

/**
 * 任务处理线程
 * 
 * @author xiejiaan
 */
public class ProcessThread implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ProcessThread.class);

	private List<TaskInfo> taskList;

	@Autowired
	private TaskRunService taskService;

	@Autowired
	private AccessChannelStatisticsService accessChannelStatisticsService;

	@Autowired
	private OemAgentClientChargeService oemAgentClientChargeService;

	@Autowired
	private ClientFailReturnService clientFailReturnService;

	public ProcessThread(List<TaskInfo> taskList) {
		SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);// 手动注入@Autowired修饰的bean
		this.taskList = taskList;
	}

	@Override
	public void run() {
		if (taskList != null && taskList.size() > 0) {
			logger.debug("分组任务开始：taskList={}", taskList);

			for (TaskInfo taskInfo : taskList) {
				runTask(taskInfo);
			}
			logger.debug("分组任务结束：taskList={}", taskList);
		}
	}

	/**
	 * 执行任务
	 * 
	 * @param taskInfo
	 */
	public void runTask(TaskInfo taskInfo) {
		boolean result = false;
		try {
			do {
				logger.debug("执行任务【开始】：taskInfo={}", taskInfo);
				Long logId = taskService.insertLog(taskInfo, null);

				switch (taskInfo.getTaskType()) {
					// 每日统计ACCESS报表（第4天前的数据）
					case access_stat:
						result = accessChannelStatisticsService.fourDaysAgo(taskInfo);
						break;
					// 每日统计ACCESS报表（昨日数据）
					case access_stat_yesterday:
						result = accessChannelStatisticsService.yesterday(taskInfo);
						break;
					// OEM代理商后付费计费，扣除代理商的余额
					case oemagent_houfufei_client_chargeOnAgent:
						result = oemAgentClientChargeService.houfufeiCharge(taskInfo);
						break;
					// 每日客户失败返还统计（四天前）
					case client_fail_return_fourday:
						result = clientFailReturnService.fourDaysAgo(taskInfo);
						break;
					case fix_history_client_fail_return_fourday:
						result = clientFailReturnService.fixDaysAgo(taskInfo);
						break;
					default:
					break;
				}

				taskService.updateLog(logId, result, null);
				logger.debug("执行任务【结束】：result={}, taskInfo={}", result, taskInfo);
				if (result) {
					if (taskInfo.getExecuteType() != ExecuteType.empty && taskInfo.getExecuteNextDate().isBeforeNow()) {
						taskService.updateExecuteNext(taskInfo);// 修改下次执行时间

						taskInfo.setExecuteNext(taskInfo.getNewExecuteNext());
						taskInfo = TaskUtils.setNewExecuteNext(taskInfo);
						if (taskInfo.isScanExecute() || taskInfo.getExecuteNextDate().isBeforeNow()) {
							continue;
						}
					}
				}
				break;
			} while (true);
		} catch (Throwable e) {
			logger.error("执行任务【失败】：taskInfo=" + taskInfo, e);
		} finally {
			if (result) {
				taskService.updateScanNext(taskInfo);// 修改下次扫描时间
			}
			Integer taskId = taskInfo.getTaskId();
			taskService.updateStatus(taskId, TaskStatus.enabled);
			TableTask.running_task_set.remove(taskId);
		}
	}

}
