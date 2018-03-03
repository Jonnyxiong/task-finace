package com.ucpaas.sms.task.constant;

/**
 * 任务常量
 *
 * @author xiejiaan
 */
public class TaskConstant {

	/**
	 * 任务类型
	 *
	 * @author xiejiaan
	 */
	public enum TaskType {

		/**
		 * 客户运维运营报表数据统计(第三天的数据)
		 */
		access_stat,
		/**
		 * 客户运维运营报表数据统计(昨日的数据)
		 */
		access_stat_yesterday,
		/**
		 * OEM代理商后付费计费，扣除代理商的余额
		 */
		oemagent_houfufei_client_chargeOnAgent,
		/**
		 * 每日客户失败返还统计（四天前）
		 */
		client_fail_return_fourday,
		/**
		 * 修复失败返还统计的历史数据（归属销售）
		 */
		fix_history_client_fail_return_fourday;

		public static TaskType getInstance(int value) {
			switch (value) {
			case 6:
				return access_stat;
			case 10:
				return access_stat_yesterday;
			case 62:
				return oemagent_houfufei_client_chargeOnAgent;
			case 33:
				return client_fail_return_fourday;
			case 97:
				return fix_history_client_fail_return_fourday;
			default:
				return null;
			}
		}
	}

	/**
	 * 执行类型
	 */
	public enum ExecuteType {
		/**
		 * 执行类型：空
		 */
		empty(null),
		/**
		 * 执行类型：分
		 */
		minute("yyyyMMddHHmm"),
		/**
		 * 执行类型：时
		 */
		hour("yyyyMMddHH"),
		/**
		 * 执行类型：日
		 */
		day("yyyyMMdd"),
		/**
		 * 执行类型：周
		 */
		week("yyyyMMdd"),
		/**
		 * 执行类型：月
		 */
		month("yyyyMM"),
		/**
		 * 执行类型：季
		 */
		season("yyyyMM"),
		/**
		 * 执行类型：年
		 */
		year("yyyy");
		private String format;// 时间格式

		ExecuteType(String format) {
			this.format = format;
		}

		public static ExecuteType getInstance(int value) {
			switch (value) {
			case 0:
				return empty;
			case 1:
				return minute;
			case 2:
				return hour;
			case 3:
				return day;
			case 4:
				return week;
			case 5:
				return month;
			case 6:
				return season;
			case 7:
				return year;
			default:
				return null;
			}
		}

		public String getFormat() {
			return format;
		}
	}

	/**
	 * 扫描类型
	 */
	public enum ScanType {
		/**
		 * 扫描类型：分
		 */
		minute,
		/**
		 * 扫描类型：时
		 */
		hour,
		/**
		 * 扫描类型：日
		 */
		day,
		/**
		 * 扫描类型：周
		 */
		week,
		/**
		 * 扫描类型：月
		 */
		month,
		/**
		 * 扫描类型：季
		 */
		season,
		/**
		 * 扫描类型：年
		 */
		year;

		public static ScanType getInstance(int value) {
			switch (value) {
			case 1:
				return minute;
			case 2:
				return hour;
			case 3:
				return day;
			case 4:
				return week;
			case 5:
				return month;
			case 6:
				return season;
			case 7:
				return year;
			default:
				return null;
			}
		}
	}

	/**
	 * 任务状态
	 *
	 * @author xiejiaan
	 */
	public enum TaskStatus {
		close(0), enabled(1), running(2), delete(3);
		private int value;

		TaskStatus(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static TaskStatus getInstance(int value) {
			switch (value) {
			case 0:
				return close;
			case 1:
				return enabled;
			case 2:
				return running;
			case 3:
				return delete;
			default:
				return null;
			}
		}
	}

}
