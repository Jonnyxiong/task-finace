package com.ucpaas.sms.task.statistic;

import com.alibaba.fastjson.JSON;
import com.jsmsframework.channel.entity.JsmsChannel;
import com.jsmsframework.channel.service.JsmsChannelService;
import com.jsmsframework.common.enums.ChargeRule;
import com.jsmsframework.common.enums.CreditRemarkType;
import com.jsmsframework.common.enums.SmsTypeEnum;
import com.jsmsframework.common.util.BeanUtil;
import com.jsmsframework.order.entity.JsmsClientOrder;
import com.jsmsframework.order.entity.JsmsOemClientPool;
import com.jsmsframework.order.service.JsmsClientOrderService;
import com.jsmsframework.order.service.JsmsOemClientPoolService;
import com.jsmsframework.sale.credit.constant.SysConstant;
import com.jsmsframework.sale.credit.service.JsmsSaleCreditService;
import com.jsmsframework.user.entity.JsmsDepartment;
import com.jsmsframework.user.service.JsmsDepartmentService;
import com.ucpaas.sms.common.util.Collections3;
import com.ucpaas.sms.common.util.FmtUtils;
import com.ucpaas.sms.common.util.StringUtils;
import com.ucpaas.sms.task.entity.access.*;
import com.ucpaas.sms.task.entity.message.*;
import com.ucpaas.sms.task.enum4sms.AccessChannelStatisticsType;
import com.ucpaas.sms.task.enum4sms.OperatorsType;
import com.ucpaas.sms.task.enum4sms.PayType;
import com.ucpaas.sms.task.exception.ClientFailReturnException;
import com.ucpaas.sms.task.service.*;
import com.ucpaas.sms.task.util.JsonUtils;
import com.ucpaas.sms.task.util.UcpaasDateUtils;
import org.apache.commons.collections.map.HashedMap;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * 需求版本2.3
 *
 * @author lpjLiu
 */
@Service
public class Deman2point3AccessStatistic implements AccessStatisticStrategy {

	private static final Logger LOGGER = LoggerFactory.getLogger("AccessChannelStatisticsService");

	@Autowired
	private AccessChannelStatisticsService accessChannelStatisticsService;

	@Autowired
	private CustomerStatTempService customerStatTempService;

	@Autowired
	private UserPriceLogService userPriceLogService;

	@Autowired
	private UserService userService;

	@Autowired
	private AccountgrRefAccountService accountgrRefAccountService;

	@Autowired
	private SmsAccessSendStatService smsAccessSendStatService;

	@Autowired
	private AgentBalanceBillService agentBalanceBillService;

	@Autowired
	private SmsBackPaymenstatService smsBackPaymenstatService;

	@Autowired
	private JsmsDepartmentService jsmsDepartmentService;

	@Autowired
	private AgentInfoService agentInfoService;

	@Autowired
	private ClientFailReturnService clientFailReturnService;

	@Autowired
	private JsmsOemClientPoolService jsmsOemClientPoolService;

	@Autowired
	private JsmsClientOrderService jsmsClientOrderService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private JsmsChannelService jsmsChannelService;

	@Autowired
	private JsmsSaleCreditService jsmsSaleCreditService;

	@Override
	public void staticAlgorithm(DateTime statDay) {
		String statTime = statDay.toString("yyyyMMdd");
		Date now = new Date();

		// 每日详细数据
		List<AccessChannelStatistics> dailyStatistics;

		// 1. 计算基础数据 客户发送统计t_sms_access_channel_statistics
		LOGGER.info("1. 计算基础数据 客户发送统计t_sms_access_channel_statistics");
		dailyStatistics = accessChannelStatistics(statDay, now);

		// 2. 计算客户发送量t_sms_access_send_stat
		LOGGER.info("2.计算客户发送量t_sms_access_send_stat");
		smsAccessSendStat(statDay, statTime, dailyStatistics, now);

		// 3. 计算回款金额统计t_sms_back_payment_stat
		Map params = new HashMap();
		DateTime beginCreatTime = new DateTime(statDay.getYear(), statDay.getMonthOfYear(), 1, 0, 0, 0);
		int endMonth = statDay.getMonthOfYear() + 1;
		int endYear = statDay.getYear();
		if (endMonth > 12) {
			endMonth = endMonth % 12;
			endYear += 1;
		}

		DateTime endCreatTime = new DateTime(endYear, endMonth, 1, 0, 0, 0);

		params.put("beginCreatTime", beginCreatTime.toDate());
		params.put("endCreatTime", endCreatTime.toDate());
		params.put("paymentType", 0); // 业务类型，0：充值，1：扣减，2：佣金转余额，3：购买产品包，4：退款，5：赠送，6：后付费客户消耗
		List<AgentBalanceBill> agentBalanceBills = agentBalanceBillService.queryAll(params);
		BigDecimal prepayRechargeMonth = BigDecimal.ZERO;

		Map<String, BigDecimal> prepayRechargeMap = new HashMap<>();
		Map<String, BigDecimal> directConsumeMap = new HashMap<>();
		String nowTime = new DateTime().toString("yyyyMMdd");
		for (AgentBalanceBill abb : agentBalanceBills) {
			String createTimeStr = new DateTime(abb.getCreateTime()).toString("yyyyMMdd");
			if (!createTimeStr.equals(nowTime)) { // 不计算任务的时间的当天
				prepayRechargeMonth = prepayRechargeMonth.add(abb.getAmount());

				BigDecimal prepayRechargeDaily = prepayRechargeMap.get(createTimeStr);
				if (prepayRechargeDaily == null)
					prepayRechargeDaily = BigDecimal.ZERO;

				prepayRechargeDaily = prepayRechargeDaily.add(abb.getAmount());
				prepayRechargeMap.put(createTimeStr, prepayRechargeDaily);
			}
		}
		BigDecimal directConsumeMonth = BigDecimal.ZERO;
		for (AccessChannelStatistics acs : dailyStatistics) {

			if (acs.getAgentId() == null || acs.getAgentId().intValue() == 0 || acs.getAgentId().intValue() == 1
					|| acs.getAgentId().intValue() == 2) { // 直客
				String dateStr = acs.getDate().toString();
				if (!dateStr.equals(nowTime)) { // 不计算任务的时间的当天
					directConsumeMonth = directConsumeMonth.add(acs.getSalefee().divide(new BigDecimal("1000")));

					BigDecimal directConsumeDaily = directConsumeMap.get(dateStr);
					if (directConsumeDaily == null)
						directConsumeDaily = BigDecimal.ZERO;

					directConsumeDaily = directConsumeDaily.add(acs.getSalefee().divide(new BigDecimal("1000")));
					directConsumeMap.put(dateStr, directConsumeDaily);
				}
			}
		}

		smsBackPaymenstatService.deleteByDateLike(statDay.toString("yyyyMM"));
		DateTime temp = beginCreatTime;
		while (temp.isBefore(endCreatTime)) {
			String dateStr = temp.toString("yyyyMMdd");
			if (dateStr.equals(nowTime)) { // 不计算任务的时间的当天e
				break;
			}
			BigDecimal prepayRechargeDaily = prepayRechargeMap.get(dateStr);
			BigDecimal directConsumeDaily = directConsumeMap.get(dateStr);
			if (prepayRechargeDaily == null)
				prepayRechargeDaily = BigDecimal.ZERO;
			if (directConsumeDaily == null)
				directConsumeDaily = BigDecimal.ZERO;
			SmsBackPaymenstat smsBackPaymenstatDaily = new SmsBackPaymenstat();
			smsBackPaymenstatDaily.setPrepayRecharge(prepayRechargeDaily);
			smsBackPaymenstatDaily.setDirectConsume(directConsumeDaily);
			smsBackPaymenstatDaily.setStattype(AccessChannelStatisticsType.daily.getValue());
			smsBackPaymenstatDaily.setDate(Integer.valueOf(dateStr));
			smsBackPaymenstatDaily.setCreatetime(now);
			smsBackPaymenstatService.insert(smsBackPaymenstatDaily);
			temp = temp.plusDays(1);
		}

		SmsBackPaymenstat smsBackPaymenstaMonth = new SmsBackPaymenstat();
		smsBackPaymenstaMonth.setPrepayRecharge(prepayRechargeMonth);
		smsBackPaymenstaMonth.setDirectConsume(directConsumeMonth);
		smsBackPaymenstaMonth.setStattype(AccessChannelStatisticsType.monthly.getValue());
		smsBackPaymenstaMonth.setDate(Integer.valueOf(statDay.toString("yyyyMM")));
		smsBackPaymenstaMonth.setCreatetime(now);
		smsBackPaymenstatService.insert(smsBackPaymenstaMonth);
	}

	private void smsAccessSendStat(DateTime statDay, String statTime, List<AccessChannelStatistics> dailyStatistics,
			Date now) {
		LOGGER.debug("smsAccessSendStat, dailyStatistics={}", JSON.toJSONString(dailyStatistics));

		// 2.1 删除当天的客户发送量统计
		// int delRow =
		// smsAccessSendStatService.deleteByDate(Integer.valueOf(statTime));
		// //删除当天的，考虑到兼容历史没有数据，所以先弄全部的
		int delRow = smsAccessSendStatService.deleteByDateLike(statDay.toString("yyyyMM"));
		LOGGER.info("删除date=" + statTime + "的客户发送量,共" + delRow + "条记录");
		Map<String, SmsAccessSendStat> dailyAccessSendStatMap = new HashMap<>(); // 只计算当天的数据
		for (AccessChannelStatistics acs : dailyStatistics) {
			// if (!acs.getDate().toString().equals(statTime)) { //只计算当天的
			// continue;

			Integer departmentId = null;
			User user = userService.getById(acs.getBelongSale());
			if (user != null) {
				JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
				if (fistLevel != null)
					departmentId = fistLevel.getDepartmentId();
			}

			AccountgrRefAccount ref = accountgrRefAccountService.getByClientid(acs.getClientid());
			Integer accountGid = null;
			if (ref != null)
				accountGid = ref.getAccountGid();

			String key = acs.getClientid() + "-" + acs.getAgentId() + "-" + acs.getBelongSale() + "-" + acs.getSmstype()
					+ "-" + acs.getPaytype() + "-" + acs.getDate() + "-" + accountGid + "-" + departmentId + "-"
					+ acs.getOperatorstype();
			SmsAccessSendStat smsAccessSendStat = dailyAccessSendStatMap.get(key);
			if (smsAccessSendStat == null) {
				smsAccessSendStat = new SmsAccessSendStat();
				smsAccessSendStat.setAgentId(acs.getAgentId());
				smsAccessSendStat.setDepartmentId(departmentId);
				smsAccessSendStat.setAccountGid(accountGid);
				smsAccessSendStat.setClientid(acs.getClientid());
				smsAccessSendStat.setBelongSale(acs.getBelongSale());
				smsAccessSendStat.setSmstype(acs.getSmstype());
				smsAccessSendStat.setPaytype(acs.getPaytype());
				smsAccessSendStat.setOperatorstype(acs.getOperatorstype());

				smsAccessSendStat.setNotsend(0);
				smsAccessSendStat.setSubmitsuccess(0);
				smsAccessSendStat.setReportsuccess(0);
				smsAccessSendStat.setSubmitfail(0);
				smsAccessSendStat.setSubretfail(0);
				smsAccessSendStat.setReportfail(0);
				smsAccessSendStat.setAuditfail(0);
				smsAccessSendStat.setRecvintercept(0);
				smsAccessSendStat.setSendintercept(0);
				smsAccessSendStat.setOverrateintercept(0);
				smsAccessSendStat.setCostfee(BigDecimal.ZERO);
				smsAccessSendStat.setSalefee(BigDecimal.ZERO);
				smsAccessSendStat.setStattype(AccessChannelStatisticsType.daily.getValue());
				smsAccessSendStat.setDate(acs.getDate());
				smsAccessSendStat.setCreatetime(now);

				// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0
				smsAccessSendStat.setChargeRule(acs.getChargeRule());
				smsAccessSendStat.setReturnTotalNumber(0);
				smsAccessSendStat.setReturnTotalAmount(BigDecimal.ZERO);
			}

			smsAccessSendStat.setNotsend(smsAccessSendStat.getNotsend() + acs.getNotsend());
			smsAccessSendStat.setSubmitsuccess(smsAccessSendStat.getSubmitsuccess() + acs.getSubmitsuccess());
			smsAccessSendStat.setReportsuccess(smsAccessSendStat.getReportsuccess() + acs.getReportsuccess());
			smsAccessSendStat.setSubmitfail(smsAccessSendStat.getSubmitfail() + acs.getSubmitfail());
			smsAccessSendStat.setSubretfail(smsAccessSendStat.getSubretfail() + acs.getSubretfail());
			smsAccessSendStat.setReportfail(smsAccessSendStat.getReportfail() + acs.getReportfail());
			smsAccessSendStat.setAuditfail(smsAccessSendStat.getAuditfail() + acs.getAuditfail());
			smsAccessSendStat.setRecvintercept(smsAccessSendStat.getRecvintercept() + acs.getRecvintercept());
			smsAccessSendStat.setSendintercept(smsAccessSendStat.getSendintercept() + acs.getSendintercept());
			smsAccessSendStat
					.setOverrateintercept(smsAccessSendStat.getOverrateintercept() + acs.getOverrateintercept());
			smsAccessSendStat.setCostfee(BigDecimal.ZERO);
			smsAccessSendStat.setSalefee(smsAccessSendStat.getSalefee().add(acs.getSalefee()));

			if (acs.getReturnTotalNumber() == null) {
				acs.setReturnTotalNumber(0);
			}
			if (acs.getReturnTotalAmount() == null) {
				acs.setReturnTotalAmount(BigDecimal.ZERO);
			}

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0
			smsAccessSendStat
					.setReturnTotalNumber(smsAccessSendStat.getReturnTotalNumber() + acs.getReturnTotalNumber());
			smsAccessSendStat
					.setReturnTotalAmount(smsAccessSendStat.getReturnTotalAmount().add(acs.getReturnTotalAmount()));

			dailyAccessSendStatMap.put(key, smsAccessSendStat);
		}
		List<SmsAccessSendStat> smsAccessSendStats = new ArrayList<>();
		Iterator<Map.Entry<String, SmsAccessSendStat>> sasIterator = dailyAccessSendStatMap.entrySet().iterator();
		while (sasIterator.hasNext()) {
			Map.Entry<String, SmsAccessSendStat> entry = sasIterator.next();
			smsAccessSendStats.add(entry.getValue());
		}
		if (smsAccessSendStats != null && !smsAccessSendStats.isEmpty()) {
			LOGGER.debug("保存每日客户发送量smsAccessSendStats={}", JSON.toJSONString(smsAccessSendStats));
			smsAccessSendStatService.insertBatch(smsAccessSendStats);
		} else {
			LOGGER.info("smsAccessSendStats每日客户发送量为空");
		}

		String monthDate = statDay.toString("yyyyMM");
		delRow = smsAccessSendStatService.deleteByDate(Integer.valueOf(monthDate));
		LOGGER.info("删除date=" + monthDate + "的客户发送量,共" + delRow + "条记录");
		Map params = new HashMap();
		params.put("stattype", AccessChannelStatisticsType.daily.getValue());
		params.put("likeDate", monthDate);
		List<SmsAccessSendStat> dailySmsAccessSendStats = smsAccessSendStatService.queryAll(params);

		Map<String, SmsAccessSendStat> monthlyAccessSendStatMap = new HashMap<>(); // 只计算当月s的数据
		for (SmsAccessSendStat sass : dailySmsAccessSendStats) {
			if (sass.getReturnTotalNumber() == null) {
				sass.setReturnTotalNumber(0);
			}
			if (sass.getReturnTotalAmount() == null) {
				sass.setReturnTotalAmount(BigDecimal.ZERO);
			}
			String key = sass.getClientid() + "-" + sass.getAgentId() + "-" + sass.getBelongSale() + "-"
					+ sass.getSmstype() + "-" + sass.getPaytype() + "-" + sass.getAccountGid() + "-"
					+ sass.getDepartmentId() + "-" + sass.getChargeRule() + "-" + sass.getOperatorstype();
			SmsAccessSendStat smsAccessSendStat = monthlyAccessSendStatMap.get(key);
			if (smsAccessSendStat == null) {
				smsAccessSendStat = new SmsAccessSendStat();
				smsAccessSendStat.setDepartmentId(sass.getDepartmentId());
				smsAccessSendStat.setAgentId(sass.getAgentId());
				smsAccessSendStat.setAccountGid(sass.getAccountGid());
				smsAccessSendStat.setClientid(sass.getClientid());
				smsAccessSendStat.setBelongSale(sass.getBelongSale());
				smsAccessSendStat.setSmstype(sass.getSmstype());
				smsAccessSendStat.setPaytype(sass.getPaytype());
				smsAccessSendStat.setOperatorstype(sass.getOperatorstype());

				smsAccessSendStat.setNotsend(0);
				smsAccessSendStat.setSubmitsuccess(0);
				smsAccessSendStat.setReportsuccess(0);
				smsAccessSendStat.setSubmitfail(0);
				smsAccessSendStat.setSubretfail(0);
				smsAccessSendStat.setReportfail(0);
				smsAccessSendStat.setAuditfail(0);
				smsAccessSendStat.setRecvintercept(0);
				smsAccessSendStat.setSendintercept(0);
				smsAccessSendStat.setOverrateintercept(0);
				smsAccessSendStat.setCostfee(BigDecimal.ZERO);
				smsAccessSendStat.setSalefee(BigDecimal.ZERO);
				smsAccessSendStat.setStattype(AccessChannelStatisticsType.monthly.getValue());
				smsAccessSendStat.setDate(Integer.valueOf(monthDate));
				smsAccessSendStat.setCreatetime(now);

				// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0
				smsAccessSendStat.setChargeRule(sass.getChargeRule());
				smsAccessSendStat.setReturnTotalNumber(0);
				smsAccessSendStat.setReturnTotalAmount(BigDecimal.ZERO);
			}

			smsAccessSendStat.setNotsend(smsAccessSendStat.getNotsend() + sass.getNotsend());
			smsAccessSendStat.setSubmitsuccess(smsAccessSendStat.getSubmitsuccess() + sass.getSubmitsuccess());
			smsAccessSendStat.setReportsuccess(smsAccessSendStat.getReportsuccess() + sass.getReportsuccess());
			smsAccessSendStat.setSubmitfail(smsAccessSendStat.getSubmitfail() + sass.getSubmitfail());
			smsAccessSendStat.setSubretfail(smsAccessSendStat.getSubretfail() + sass.getSubretfail());
			smsAccessSendStat.setReportfail(smsAccessSendStat.getReportfail() + sass.getReportfail());
			smsAccessSendStat.setAuditfail(smsAccessSendStat.getAuditfail() + sass.getAuditfail());
			smsAccessSendStat.setRecvintercept(smsAccessSendStat.getRecvintercept() + sass.getRecvintercept());
			smsAccessSendStat.setSendintercept(smsAccessSendStat.getSendintercept() + sass.getSendintercept());
			smsAccessSendStat
					.setOverrateintercept(smsAccessSendStat.getOverrateintercept() + sass.getOverrateintercept());
			smsAccessSendStat.setCostfee(smsAccessSendStat.getCostfee().add(sass.getCostfee()));
			smsAccessSendStat.setSalefee(smsAccessSendStat.getSalefee().add(sass.getSalefee()));

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0
			smsAccessSendStat
					.setReturnTotalNumber(smsAccessSendStat.getReturnTotalNumber() + sass.getReturnTotalNumber());
			smsAccessSendStat
					.setReturnTotalAmount(smsAccessSendStat.getReturnTotalAmount().add(sass.getReturnTotalAmount()));

			monthlyAccessSendStatMap.put(key, smsAccessSendStat);

		}

		smsAccessSendStats = new ArrayList<>();
		sasIterator = monthlyAccessSendStatMap.entrySet().iterator();
		while (sasIterator.hasNext()) {
			Map.Entry<String, SmsAccessSendStat> entry = sasIterator.next();
			smsAccessSendStats.add(entry.getValue());
		}
		if (smsAccessSendStats != null && !smsAccessSendStats.isEmpty()) {
			LOGGER.debug("保存每月客户发送量smsAccessSendStats={}", JSON.toJSONString(smsAccessSendStats));
			smsAccessSendStatService.insertBatch(smsAccessSendStats);
		} else {
			LOGGER.info("smsAccessSendStats每月客户发送量为空");
		}
	}

	private List<AccessChannelStatistics> accessChannelStatistics(DateTime statDay, Date now) {

		String statTime = statDay.toString("yyyyMMdd");

		/**
		 * 每个客户在每个通道下面的明细数据(通道0和不为0是分开统计)
		 * 通道为0的情况下，也分两种状态去统计，一种是失败(state=4)另一种是拦截(state=0/5/7/8/9/10)
		 */
		// 每日详细数据 stattype=0
		List<AccessChannelStatistics> dailyStatistics = new ArrayList<>();

		// 每日合计数据 stattype=1
		List<AccessChannelStatistics> dailySumStatistics = new ArrayList<>();

		// 每月详细数据 stattype=2
		List<AccessChannelStatistics> monthlyStatistics = new ArrayList<>();

		// 每月合计数据 stattype=3
		List<AccessChannelStatistics> monthlySumStatistics = new ArrayList<>();

		List<CustomerStatTemp> customerStatTemps = customerStatTempService.generateDataIncludeAgentId(statTime);
		if (customerStatTemps == null || customerStatTemps.size() == 0) {
			LOGGER.debug("根据时间段，遍历所有的access表，生成CustomerStatTemp临时数据为空，统计结束 ");

			// Mod by lpjLiu 2017-10-23 SMS-1067 若当天的数据为空，查询月当月的所有数据，直接返回，保证
			// t_sms_access_send_stat统计没有问题
			dailyStatistics = getEveryDayOfMonthAccessChannelStatistics(statTime);
			return dailyStatistics;
		}

		LOGGER.debug("清除统计的数据时间date={}的数据 ", statTime);
		accessChannelStatisticsService.deleteByDate(statTime);
		LOGGER.debug("根据时间段，遍历所有的access表，生成CustomerStatTemp临时数据{} ", JSON.toJSONString(customerStatTemps));

		Map<String, AccessChannelStatistics> temp = new HashMap<>();
		for (CustomerStatTemp cst : customerStatTemps) {

			if (cst.getOperatorstype() == null) {
				JsmsChannel jsmsChannel = jsmsChannelService.getById(cst.getChannelid());
				if (jsmsChannel == null) {
					cst.setOperatorstype(OperatorsType.interupt.getValue());
				} else {
					cst.setOperatorstype(jsmsChannel.getOperatorstype());
				}
			}

			// 分组条件clientid + agent_id + product_type + channelid + paytype +
			// belong_sale + smstype + sub_id
			String key = cst.getClientid() + "-" + "-" + cst.getAgentId() + cst.getProductType() + "-"
					+ cst.getChannelid() + "-" + cst.getPaytype() + "-" + cst.getBelongSale() + "-" + cst.getSmstype()
					+ "-" + cst.getSubId() + cst.getOperatorstype();
			// Account account =
			// accountService.getByClientid(cst.getClientid());
			AccessChannelStatistics acs = temp.get(key);
			if (acs == null) {
				acs = new AccessChannelStatistics();
				acs.setAgentId(cst.getAgentId().intValue());
				acs.setClientid(cst.getClientid());
				acs.setName(cst.getUsername());
				acs.setSid(cst.getSid());
				acs.setPaytype(cst.getPaytype());
				acs.setOperatorstype(cst.getOperatorstype());
				acs.setChannelid(cst.getChannelid());
				acs.setRemark(cst.getChannelremark());
				acs.setProductType(cst.getProductType());
				acs.setSubId(cst.getSubId());
				acs.setStattype(AccessChannelStatisticsType.daily.getValue());
				acs.setDate(cst.getDate());
				acs.setCreatetime(now);
				acs.setChargetotal(0);
				acs.setOverrateChargeTotal(0);
				acs.setCostfee(BigDecimal.ZERO);
				acs.setSalefee(BigDecimal.ZERO);
				acs.setProductfee(BigDecimal.ZERO);
				acs.setUsersmstotal(0);
				acs.setSendtotal(0);
				acs.setNotsend(0);
				acs.setSubmitsuccess(0);
				acs.setReportsuccess(0);
				acs.setSubmitfail(0);
				acs.setSubretfail(0);
				acs.setReportfail(0);
				acs.setAuditfail(0);
				acs.setRecvintercept(0);
				acs.setSendintercept(0);
				acs.setOverrateintercept(0);
				acs.setBelongSale(cst.getBelongSale());
				acs.setSmstype(cst.getSmstype());

				// Add by lpjLiu 2017-10-11 v2.3.0 设置计费规则
				acs.setChargeRule(cst.getChargeRule());
				acs.setReturnTotalAmount(BigDecimal.ZERO);
				acs.setReturnTotalNumber(0);
			}

			// 获取信息
			Tuple<Integer, BigDecimal, BigDecimal, Integer> decideInfo = decideChargeTotal_Salefee_ProductFee_ReturnTotal(
					cst, statDay);

			// 消费金额(针对客户) = 产品销售价 *计费条数，由于在通道id=0的情况下，超频计费条数为0，所以不用算超频计费
			int chargetTotal = decideInfo.getFirst();
			BigDecimal salefee = decideInfo.getSecond();
			BigDecimal productfee = decideInfo.getThree();
			int costChargeTotal = cst.getSubmitsuccess() + cst.getReportsuccess(); // 成本的计费条数=1、3
			int returnTotal = decideInfo.getFour();

			// 计费条数累加
			acs.setChargetotal(acs.getChargetotal() + chargetTotal);

			// 超频计费条数，在正常数据中，通道id=0，所以这里会为0
			acs.setOverrateChargeTotal(acs.getOverrateChargeTotal() + cst.getOverrateChargeTotal());

			// 通道成本 *计费条数，由于在通道id=0的情况下，超频计费条数为0，所以不用算超频计费
			BigDecimal costfee = cst.getCostfee().multiply(new BigDecimal(costChargeTotal));
			acs.setCostfee(acs.getCostfee().add(costfee));

			// 消费金额(针对客户) = 产品销售价 *计费条数 计费条数和产品销售价上面有定义
			acs.setSalefee(acs.getSalefee().add(salefee.multiply(new BigDecimal(chargetTotal))));

			// 消耗成本(针对代理商) = 产品成本价 *计费条数，由于在通道id=0的情况下，超频计费条数为0，所以不用算超频计费
			// BigDecimal productfee = cst.getProductfee().multiply(new
			// BigDecimal(chargetTotal));
			acs.setProductfee(acs.getProductfee().add(productfee.multiply(new BigDecimal(chargetTotal))));

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还条数累加
			acs.setReturnTotalNumber(acs.getReturnTotalNumber() + returnTotal);

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还金额累加
			acs.setReturnTotalAmount(acs.getReturnTotalAmount().add(salefee.multiply(new BigDecimal(returnTotal))));

			// 接受短信总条数 usersmstotal = 0 + 1 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10
			// 通道不为0的情况下，正常情况下不应该存在0 + 5 + 6 + 7 + 8 + 9 + 10，存在则为异常
			int usersmstotal = cst.getNotsend() + cst.getSubmitsuccess() + cst.getReportsuccess() + cst.getSubmitfail()
					+ cst.getSubretfail() + cst.getReportfail() + cst.getAuditfail() + cst.getRecvintercept()
					+ cst.getSendintercept() + cst.getOverrateintercept();
			acs.setUsersmstotal(acs.getUsersmstotal() + usersmstotal);

			// 发送短信总条数 sendtotal = 1 + 3 + 6
			int sendTotal = cst.getSubmitsuccess() + cst.getReportsuccess() + cst.getReportfail();
			acs.setSendtotal(acs.getSendtotal() + sendTotal);

			acs.setNotsend(acs.getNotsend() + cst.getNotsend());
			acs.setSubmitsuccess(acs.getSubmitsuccess() + cst.getSubmitsuccess());
			acs.setReportsuccess(acs.getReportsuccess() + cst.getReportsuccess());
			acs.setSubmitfail(acs.getSubmitfail() + cst.getSubmitfail());
			acs.setSubretfail(acs.getSubretfail() + cst.getSubretfail());
			acs.setReportfail(acs.getReportfail() + cst.getReportfail());
			acs.setAuditfail(acs.getAuditfail() + cst.getAuditfail());
			acs.setRecvintercept(acs.getRecvintercept() + cst.getRecvintercept());
			acs.setSendintercept(acs.getSendintercept() + cst.getSendintercept());
			acs.setOverrateintercept(acs.getOverrateintercept() + cst.getOverrateintercept());

			temp.put(key, acs);
		}

		/**
		 * 完成每日详细数据
		 */
		for (Map.Entry<String, AccessChannelStatistics> tempEntry : temp.entrySet()) {
			dailyStatistics.add(tempEntry.getValue());
		}

		LOGGER.debug("完成每日详细数据的三种统计（通道不为0、state=4、拦截），三种统计数据不应该互相有重叠  数据{} ", JSON.toJSONString(dailyStatistics));

		if (dailyStatistics.size() > 0) {
			accessChannelStatisticsService.insertBatch(dailyStatistics);
		} else {
			LOGGER.info("客户运营运维统计报表任务【结束】：计算出来的每日统计数据为空");
			return dailyStatistics;
		}

		/**
		 * 每日合计数据统计
		 */
		temp = new HashMap<>();
		for (AccessChannelStatistics cst : dailyStatistics) {
			String key = cst.getClientid();

			AccessChannelStatistics acs = temp.get(key);
			if (acs == null) {
				acs = new AccessChannelStatistics();
				acs.setAgentId(cst.getAgentId().intValue());
				acs.setClientid(cst.getClientid());
				acs.setName(cst.getName());
				acs.setSid("");
				acs.setPaytype(-1); // 付费类型，0：预付费，1：后付费，负数则为 -1:合计
				acs.setOperatorstype(-2); // 通道运营商类型:0->全网，1->移动，2->联通，3->电信，4->国际；（为负数时表示数据类型，-1表示拦截
				// -2表示合计）
				acs.setChannelid(-1); // 负数则为 -1:合计
				acs.setRemark(" - ");
				acs.setProductType(-1);
				acs.setSubId(" - ");
				acs.setStattype(AccessChannelStatisticsType.dailySum.getValue());
				acs.setDate(cst.getDate());
				acs.setCreatetime(now);

				acs.setChargetotal(0);
				acs.setOverrateChargeTotal(0);
				acs.setCostfee(BigDecimal.ZERO);
				acs.setSalefee(BigDecimal.ZERO);
				acs.setProductfee(BigDecimal.ZERO);
				acs.setUsersmstotal(0);
				acs.setSendtotal(0);
				acs.setNotsend(0);
				acs.setSubmitsuccess(0);
				acs.setReportsuccess(0);
				acs.setSubmitfail(0);
				acs.setSubretfail(0);
				acs.setReportfail(0);
				acs.setAuditfail(0);
				acs.setRecvintercept(0);
				acs.setSendintercept(0);
				acs.setOverrateintercept(0);
				acs.setBelongSale(null);
				acs.setSmstype(null);

				// Add by lpjLiu 2017-10-11 v2.3.0 设置计费规则
				acs.setChargeRule(cst.getChargeRule());
				acs.setReturnTotalAmount(BigDecimal.ZERO);
				acs.setReturnTotalNumber(0);
			}

			acs.setChargetotal(acs.getChargetotal() + cst.getChargetotal());
			acs.setOverrateChargeTotal(acs.getOverrateChargeTotal() + cst.getOverrateChargeTotal());
			acs.setCostfee(acs.getCostfee().add(cst.getCostfee()));
			acs.setSalefee(acs.getSalefee().add(cst.getSalefee()));
			acs.setProductfee(acs.getProductfee().add(cst.getProductfee()));
			acs.setUsersmstotal(acs.getUsersmstotal() + cst.getUsersmstotal());
			acs.setSendtotal(acs.getSendtotal() + cst.getSendtotal());
			acs.setNotsend(acs.getNotsend() + cst.getNotsend());
			acs.setSubmitsuccess(acs.getSubmitsuccess() + cst.getSubmitsuccess());
			acs.setReportsuccess(acs.getReportsuccess() + cst.getReportsuccess());
			acs.setSubmitfail(acs.getSubmitfail() + cst.getSubmitfail());
			acs.setSubretfail(acs.getSubretfail() + cst.getSubretfail());
			acs.setReportfail(acs.getReportfail() + cst.getReportfail());
			acs.setAuditfail(acs.getAuditfail() + cst.getAuditfail());
			acs.setRecvintercept(acs.getRecvintercept() + cst.getRecvintercept());
			acs.setSendintercept(acs.getSendintercept() + cst.getSendintercept());
			acs.setOverrateintercept(acs.getOverrateintercept() + cst.getOverrateintercept());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还条数累加
			acs.setReturnTotalNumber(acs.getReturnTotalNumber() + cst.getReturnTotalNumber());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还金额累加
			acs.setReturnTotalAmount(acs.getReturnTotalAmount().add(cst.getReturnTotalAmount()));

			temp.put(key, acs);
		}

		/**
		 * 完成每日合计数据的统计
		 */
		for (Map.Entry<String, AccessChannelStatistics> tempEntry : temp.entrySet()) {
			dailySumStatistics.add(tempEntry.getValue());
		}
		LOGGER.debug(" 完成每日合计数据的统计  数据{} ", JSON.toJSONString(dailySumStatistics));
		accessChannelStatisticsService.insertBatch(dailySumStatistics);

		// 清除t_sms_access_channel_statistics 表统计月的老数据
		statTime = statDay.toString("yyyyMM");
		LOGGER.debug(" 清除t_sms_access_channel_statistics 表统计月的老数据 , date={} ", statTime);
		accessChannelStatisticsService.deleteByDate(statTime);
		dailyStatistics = getEveryDayOfMonthAccessChannelStatistics(statTime);

		/**
		 * 每月详细数据统计
		 */
		temp = new HashMap<>();
		for (AccessChannelStatistics cst : dailyStatistics) {
			if (cst.getReturnTotalNumber() == null) {
				cst.setReturnTotalNumber(0);
			}
			if (cst.getReturnTotalAmount() == null) {
				cst.setReturnTotalAmount(BigDecimal.ZERO);
			}
			String key = cst.getClientid() + "-" + "-" + cst.getAgentId() + cst.getProductType() + "-"
					+ cst.getChannelid() + "-" + cst.getPaytype() + "-" + cst.getBelongSale() + "-" + cst.getSmstype()
					+ "-" + cst.getSubId() + cst.getOperatorstype() + "-" + cst.getChargeRule();
			AccessChannelStatistics acs = temp.get(key);
			if (acs == null) {
				acs = new AccessChannelStatistics();
				acs.setAgentId(cst.getAgentId().intValue());
				acs.setClientid(cst.getClientid());
				acs.setName(cst.getName());
				acs.setSid(cst.getSid());
				acs.setPaytype(cst.getPaytype()); // 付费类型，0：预付费，1：后付费，负数则为 -1:合计
				acs.setOperatorstype(cst.getOperatorstype()); // 通道运营商类型:0->全网，1->移动，2->联通，3->电信，4->国际；（为负数时表示数据类型，-1表示拦截
				// -2表示合计）
				acs.setChannelid(cst.getChannelid()); // 负数则为 -1:合计
				acs.setRemark(cst.getRemark());
				acs.setProductType(cst.getProductType());
				acs.setSubId(cst.getSubId());
				acs.setStattype(AccessChannelStatisticsType.monthly.getValue());
				acs.setDate(cst.getDate() / 100);
				acs.setCreatetime(now);

				acs.setChargetotal(0);
				acs.setOverrateChargeTotal(0);
				acs.setCostfee(BigDecimal.ZERO);
				acs.setSalefee(BigDecimal.ZERO);
				acs.setProductfee(BigDecimal.ZERO);
				acs.setUsersmstotal(0);
				acs.setSendtotal(0);
				acs.setNotsend(0);
				acs.setSubmitsuccess(0);
				acs.setReportsuccess(0);
				acs.setSubmitfail(0);
				acs.setSubretfail(0);
				acs.setReportfail(0);
				acs.setAuditfail(0);
				acs.setRecvintercept(0);
				acs.setSendintercept(0);
				acs.setOverrateintercept(0);
				acs.setBelongSale(cst.getBelongSale());
				acs.setSmstype(cst.getSmstype());

				// Add by lpjLiu 2017-10-11 v2.3.0 设置计费规则
				acs.setChargeRule(cst.getChargeRule());
				acs.setReturnTotalAmount(BigDecimal.ZERO);
				acs.setReturnTotalNumber(0);
			}

			acs.setChargetotal(acs.getChargetotal() + cst.getChargetotal());
			acs.setOverrateChargeTotal(acs.getOverrateChargeTotal() + cst.getOverrateChargeTotal());
			acs.setCostfee(acs.getCostfee().add(cst.getCostfee()));
			acs.setSalefee(acs.getSalefee().add(cst.getSalefee()));
			acs.setProductfee(acs.getProductfee().add(cst.getProductfee()));
			acs.setUsersmstotal(acs.getUsersmstotal() + cst.getUsersmstotal());
			acs.setSendtotal(acs.getSendtotal() + cst.getSendtotal());
			acs.setNotsend(acs.getNotsend() + cst.getNotsend());
			acs.setSubmitsuccess(acs.getSubmitsuccess() + cst.getSubmitsuccess());
			acs.setReportsuccess(acs.getReportsuccess() + cst.getReportsuccess());
			acs.setSubmitfail(acs.getSubmitfail() + cst.getSubmitfail());
			acs.setSubretfail(acs.getSubretfail() + cst.getSubretfail());
			acs.setReportfail(acs.getReportfail() + cst.getReportfail());
			acs.setAuditfail(acs.getAuditfail() + cst.getAuditfail());
			acs.setRecvintercept(acs.getRecvintercept() + cst.getRecvintercept());
			acs.setSendintercept(acs.getSendintercept() + cst.getSendintercept());
			acs.setOverrateintercept(acs.getOverrateintercept() + cst.getOverrateintercept());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还条数累加
			acs.setReturnTotalNumber(acs.getReturnTotalNumber() + cst.getReturnTotalNumber());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还金额累加
			acs.setReturnTotalAmount(acs.getReturnTotalAmount().add(cst.getReturnTotalAmount()));

			temp.put(key, acs);
		}

		/**
		 * 完成每月详细数据的统计
		 */
		for (Map.Entry<String, AccessChannelStatistics> tempEntry : temp.entrySet()) {
			monthlyStatistics.add(tempEntry.getValue());
		}
		LOGGER.debug(" 完成每月详细数据的统计  数据{} ", JSON.toJSONString(monthlyStatistics));
		accessChannelStatisticsService.insertBatch(monthlyStatistics);

		/**
		 * 每月合计数据统计
		 */
		temp = new HashMap<>();
		for (AccessChannelStatistics cst : monthlyStatistics) {
			String key = cst.getClientid();

			AccessChannelStatistics acs = temp.get(key);
			if (acs == null) {
				acs = new AccessChannelStatistics();
				acs.setAgentId(cst.getAgentId().intValue());
				acs.setClientid(cst.getClientid());
				acs.setName(cst.getName());
				acs.setSid(cst.getSid());
				acs.setPaytype(-1); // 付费类型，0：预付费，1：后付费，负数则为 -1:合计
				acs.setOperatorstype(-2); // -1表示拦截 -2表示合计
				// 通道运营商类型:0->全网，1->移动，2->联通，3->电信，4->国际；（为负数时表示数据类型，-1表示拦截
				// -2表示合计）
				acs.setChannelid(-1); // 负数则为 -1:合计
				acs.setRemark(" - ");
				acs.setProductType(-1);
				acs.setSubId(" - ");
				acs.setStattype(AccessChannelStatisticsType.monthlySum.getValue());
				acs.setDate(cst.getDate());
				acs.setCreatetime(now);

				acs.setChargetotal(0);
				acs.setOverrateChargeTotal(0);
				acs.setCostfee(BigDecimal.ZERO);
				acs.setSalefee(BigDecimal.ZERO);
				acs.setProductfee(BigDecimal.ZERO);
				acs.setUsersmstotal(0);
				acs.setSendtotal(0);
				acs.setNotsend(0);
				acs.setSubmitsuccess(0);
				acs.setReportsuccess(0);
				acs.setSubmitfail(0);
				acs.setSubretfail(0);
				acs.setReportfail(0);
				acs.setAuditfail(0);
				acs.setRecvintercept(0);
				acs.setSendintercept(0);
				acs.setOverrateintercept(0);
				acs.setBelongSale(null);
				acs.setSmstype(null);

				// Add by lpjLiu 2017-10-11 v2.3.0 设置计费规则
				acs.setChargeRule(-1);
				acs.setReturnTotalAmount(BigDecimal.ZERO);
				acs.setReturnTotalNumber(0);
			}

			acs.setChargetotal(acs.getChargetotal() + cst.getChargetotal());
			acs.setOverrateChargeTotal(acs.getOverrateChargeTotal() + cst.getOverrateChargeTotal());
			acs.setCostfee(acs.getCostfee().add(cst.getCostfee()));
			acs.setSalefee(acs.getSalefee().add(cst.getSalefee()));
			acs.setProductfee(acs.getProductfee().add(cst.getProductfee()));
			acs.setUsersmstotal(acs.getUsersmstotal() + cst.getUsersmstotal());
			acs.setSendtotal(acs.getSendtotal() + cst.getSendtotal());
			acs.setNotsend(acs.getNotsend() + cst.getNotsend());
			acs.setSubmitsuccess(acs.getSubmitsuccess() + cst.getSubmitsuccess());
			acs.setReportsuccess(acs.getReportsuccess() + cst.getReportsuccess());
			acs.setSubmitfail(acs.getSubmitfail() + cst.getSubmitfail());
			acs.setSubretfail(acs.getSubretfail() + cst.getSubretfail());
			acs.setReportfail(acs.getReportfail() + cst.getReportfail());
			acs.setAuditfail(acs.getAuditfail() + cst.getAuditfail());
			acs.setRecvintercept(acs.getRecvintercept() + cst.getRecvintercept());
			acs.setSendintercept(acs.getSendintercept() + cst.getSendintercept());
			acs.setOverrateintercept(acs.getOverrateintercept() + cst.getOverrateintercept());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还条数累加
			acs.setReturnTotalNumber(acs.getReturnTotalNumber() + cst.getReturnTotalNumber());

			// Add by lpjLiu 2017-10-11 v2.3.0 v5.15.0 返还金额累加
			acs.setReturnTotalAmount(acs.getReturnTotalAmount().add(cst.getReturnTotalAmount()));

			temp.put(key, acs);
		}

		/**
		 * 完成每月合计数据的统计
		 */
		for (Map.Entry<String, AccessChannelStatistics> tempEntry : temp.entrySet()) {
			monthlySumStatistics.add(tempEntry.getValue());
		}
		LOGGER.debug(" 完成每月合计数据的统计  数据{} ", JSON.toJSONString(monthlySumStatistics));
		accessChannelStatisticsService.insertBatch(monthlySumStatistics);
		return dailyStatistics;
	}

	private List<AccessChannelStatistics> getEveryDayOfMonthAccessChannelStatistics(String statTime) {
		List<AccessChannelStatistics> dailyStatistics;// 获得当月所有已经存在的每日明细数据
		Map<String, Object> params = new HashMap<>();
		params.put("stattype", AccessChannelStatisticsType.daily.getValue());
		params.put("dataTimePrix", statTime.substring(0, 6));

		dailyStatistics = accessChannelStatisticsService.queryMonthly(params);
		return dailyStatistics;
	}

	private Tuple<Integer, BigDecimal, BigDecimal, Integer> decideChargeTotal_Salefee_ProductFee_ReturnTotal(
			CustomerStatTemp cst, DateTime statDay) {
		int chargeTotal = 0;
		int returnTotal = 0;

		// 根据计费规则算出计费条数
		if (cst.getChargeRule().intValue() == ChargeRule.明确成功量计费.getValue().intValue()) {
			// 计费条数 chargeTotal = 3
			chargeTotal = cst.getReportsuccess();

			/*
			 * // 牛姐说 （文杰、鹏举、牛姐讨论） 通道号为0的状态为1的不做退费 2017-11-08 这是异常数据 if
			 * (cst.getChannelid() == null || cst.getChannelid() == 0){ // 需返还条数
			 * returnTotal = 4 + 6 returnTotal = cst.getSubmitfail() +
			 * cst.getReportfail(); } else { // 需返还条数 returnTotal = 1 + 4 + 6
			 * returnTotal = cst.getSubmitsuccess() + cst.getSubmitfail() +
			 * cst.getReportfail(); }
			 */

			// 不需要上面的处理
			// 需返还条数 returnTotal = 1 + 4 + 6
			returnTotal = cst.getSubmitsuccess() + cst.getSubmitfail() + cst.getReportfail();
		} else if (cst.getChargeRule().intValue() == ChargeRule.成功量计费.getValue().intValue()) {
			// 计费条数 chargeTotal = 1 + 3
			chargeTotal = cst.getSubmitsuccess() + cst.getReportsuccess();

			// 需返还条数 returnTotal = 4 + 6
			returnTotal = cst.getSubmitfail() + cst.getReportfail();
		} else {
			// 其它一律提交量计费
			// 计费条数 chargeTotal = 1 + 3 + 4 + 6
			chargeTotal = cst.getSubmitsuccess() + cst.getReportsuccess() + cst.getSubmitfail() + cst.getReportfail();
		}

		// 算出销售金额
		BigDecimal saleFee = BigDecimal.ZERO;
		if (cst.getPaytype().intValue() == PayType.prepayment.getValue().intValue()) { // 预付费客户
			saleFee = cst.getSalefee();
		} else { // 后付费客户
			// 国际与非国际的价格不一样
			if (cst.getOperatorstype() != null && cst.getOperatorstype().intValue() == 4) { // 国际
				saleFee = cst.getSalefee();
			} else { // 非国际的取生效日期的价格
				UserPriceLog userPriceLog = userPriceLogService.getByPrice(cst.getClientid(), cst.getSmstype(),
						statDay);
				if (userPriceLog == null)
					saleFee = BigDecimal.ZERO;
				else {
					saleFee = userPriceLog.getUserPrice().multiply(new BigDecimal("1000"));
				}
			}
		}

		// 算出产品成本
		BigDecimal productfee = BigDecimal.ZERO;
		if (cst.getPaytype().intValue() == PayType.prepayment.getValue().intValue()) { // 预付费客户
			productfee = cst.getProductfee();
		} else { // 后付费客户
			// 国际与非国际的价格不一样
			if (cst.getOperatorstype() != null && cst.getOperatorstype().intValue() == 4) { // 国际
				productfee = cst.getProductfee();
			} else { // 非国际的取0
				productfee = BigDecimal.ZERO;
			}
		}

		return new Tuple<>(chargeTotal, saleFee, productfee, returnTotal);
	}

	class Tuple<T, K, E, F> {
		private T t;
		private K k;
		private E e;
		private F f;

		public Tuple(T t, K k, E e, F f) {
			this.t = t;
			this.k = k;
			this.e = e;
			this.f = f;
		}

		public T getFirst() {
			return t;
		}

		public K getSecond() {
			return k;
		}

		public E getThree() {
			return e;
		}

		public F getFour() {
			return f;
		}
	}

	/**
	 * 客户失败返回清单表
	 * 
	 * @param statDay
	 */
	@Override
	public void staticClientFailReturn(DateTime statDay) {
		String statTime = statDay.toString("yyyyMMdd");
		LOGGER.debug("客户失败返返清单表任务开始 表=t_sms_client_fail_return, 统计日期={}", statTime);

		Date now = new Date();
		String statTimeDesc = statDay.getMonthOfYear() + "-" + statDay.getDayOfMonth();

		// 1. 已经统计过，不再统计，因为OEM会自动返还，所以不能重复统计
		Map<String, Object> params = new HashMap<>();
		params.put("date", statTime);
		int count = clientFailReturnService.count(params);
		if (count > 0) {
			LOGGER.debug("客户失败返： {} 已经执行过统计，不再继续统计", statTime);
			return;
		}

		// 2. 删除当天的统计信息
		clientFailReturnService.deleteByDate(Integer.parseInt(statTime));

		LOGGER.debug("1. 计算基础数据 客户失败返回清单 t_sms_client_fail_return");

		// 查询所有的OEM代理商
		List<String> oemAgentIds = agentInfoService.findOemAgentIdList();

		// 3. 品牌、销售、OEM代理商预付费用户
		List<AccessChannelStatistics> yufuList = clientFailReturnService
				.findYuFuForClientFailReturnByDay(Integer.parseInt(statTime));

		Map<String, Integer> deptList = new HashMap<>();
		LOGGER.debug("客户失败返：品牌、销售、OEM代理商预付费ACCESS统计列表 {}", JSON.toJSONString(yufuList));
		if (Collections3.isEmpty(yufuList)) {
			LOGGER.debug("客户失败返：品牌、销售、OEM代理商预付费ACCESS统计列表为空");
		} else {
			List<ClientFailReturn> yufuClientFailList = new ArrayList<>();
			ClientFailReturn cfr;
			for (AccessChannelStatistics stat : yufuList) {
				// 返还条数为0的时候不需要插入
				if (stat.getReturnTotalNumber() == null || stat.getReturnTotalNumber() <= 0) {
					continue;
				}

				cfr = new ClientFailReturn();
				cfr.setClientid(stat.getClientid());
				cfr.setPaytype(stat.getPaytype());
				cfr.setChargeRule(stat.getChargeRule());
				cfr.setSmstype(stat.getSmstype());
				cfr.setSubId(stat.getSubId());
				cfr.setSubmitsuccess(stat.getSubmitsuccess());
				cfr.setSubmitfail(stat.getSubmitfail());
				cfr.setReportfail(stat.getReportfail());
				cfr.setReturnNumber(stat.getReturnTotalNumber());
				cfr.setRefundState(0);
				cfr.setDate(Integer.parseInt(statTime));
				cfr.setCreateTime(now);
				cfr.setBelongSale(stat.getBelongSale());

				// 放入代理商ID
				cfr.setAgentId(stat.getAgentId());
				// 放入运营商类型
				cfr.setOperatorstype(stat.getOperatorstype());

				// 放入部门ID
				if (null == cfr.getBelongSale())
				{
					cfr.setDepartmentId(null);
				} else {
					Integer deptId = deptList.get(cfr.getBelongSale().toString());
					if (null == deptId)
					{
						User user = userService.getById(cfr.getBelongSale());
						if (user != null) {
							JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
							if (fistLevel != null)
								deptId = fistLevel.getDepartmentId();
						}

						// 将查询到的部门放入缓存中
						deptList.put(cfr.getBelongSale().toString(), null == deptId ? -1 : deptId);
						cfr.setDepartmentId(deptId);
					} else {
						cfr.setDepartmentId(deptId == -1 ? null: deptId);
					}
				}

				if (oemAgentIds.contains(stat.getAgentId().toString())) {
					// 查询代理商池
					JsmsOemClientPool oemClientPool = jsmsOemClientPoolService
							.getByClientPoolId(Long.parseLong(cfr.getSubId()));
					if (oemClientPool == null) {
						LOGGER.error("客户失败返：OEM代理商客户根据SUBID查询客户池为空，clientId={}，sub_id={}", cfr.getClientid(),
								cfr.getSubId());
						throw new ClientFailReturnException("客户失败返：OEM代理商客户根据SUBID查询客户池为空，clientId=" + cfr.getClientid()
								+ "，sub_id=" + cfr.getSubId());
					}

					// 设置值
					cfr.setProductType(oemClientPool.getProductType());
					cfr.setOperatorCode(oemClientPool.getOperatorCode());
					cfr.setAreaCode(oemClientPool.getAreaCode());
					cfr.setDueTime(oemClientPool.getDueTime());
					cfr.setUnitPrice(oemClientPool.getUnitPrice());
				} else {
					// 查询客户订单
					JsmsClientOrder clientOrder = jsmsClientOrderService.getBySubId(Long.parseLong(cfr.getSubId()));
					if (clientOrder == null) {
						LOGGER.error("客户失败返：品牌代理商客户根据SUBID查询客户订单为空，clientId={}，sub_id={}", cfr.getClientid(),
								cfr.getSubId());
						throw new ClientFailReturnException("客户失败返：品牌代理商客户根据SUBID查询客户订单为空，clientId=" + cfr.getClientid()
								+ "，sub_id=" + cfr.getSubId());
					}

					// 设置值
					cfr.setProductType(clientOrder.getProductType());
					cfr.setOperatorCode(clientOrder.getOperatorCode());
					cfr.setAreaCode(clientOrder.getAreaCode());
					cfr.setDueTime(clientOrder.getEndTime());
					cfr.setUnitPrice(clientOrder.getUnitPrice());
				}

				yufuClientFailList.add(cfr);
			}

			if (yufuClientFailList.size() > 0) {
				LOGGER.debug("客户失败返：品牌、销售、OEM代理商预付费 批量插入返还列表 {}", JSON.toJSONString(yufuClientFailList));
				clientFailReturnService.insertBatch(yufuClientFailList);
			}
		}

		// 4. OEM代理商后付费用户
		List<AccessChannelStatistics> oemHouFuList = clientFailReturnService
				.findOemHouFuForClientFailReturnByDay(Integer.parseInt(statTime), oemAgentIds);
		LOGGER.debug("客户失败返：OEM代理商后付费用户ACCESS统计列表 {}", JSON.toJSONString(oemHouFuList));
		if (Collections3.isEmpty(oemHouFuList)) {
			LOGGER.debug("客户失败返：OEM代理商后付费用户ACCESS统计列表为空");
			return;
		}

		List<ClientFailReturn> houfuClientFailList = new ArrayList<>();
		Map<String, String> unitPrices = new HashMap<>();
		ClientFailReturn cfr;
		for (AccessChannelStatistics stat : oemHouFuList) {
			// 返还条数为0的时候不需要插入
			if (stat.getReturnTotalNumber() == null || stat.getReturnTotalNumber() <= 0) {
				continue;
			}
			cfr = new ClientFailReturn();
			cfr.setClientid(stat.getClientid());
			cfr.setPaytype(stat.getPaytype());
			cfr.setChargeRule(stat.getChargeRule());
			cfr.setSmstype(stat.getSmstype());
			cfr.setSubId(null);
			cfr.setSubmitsuccess(stat.getSubmitsuccess());
			cfr.setSubmitfail(stat.getSubmitfail());
			cfr.setReportfail(stat.getReportfail());
			cfr.setReturnNumber(stat.getReturnTotalNumber());
			cfr.setRefundState(1); // OEM后付费后面直接返还
			cfr.setDate(Integer.parseInt(statTime));
			cfr.setCreateTime(now);
			cfr.setBelongSale(stat.getBelongSale());
			cfr.setProductType(null);
			cfr.setOperatorCode(null);
			cfr.setAreaCode(null);
			cfr.setDueTime(null);

			// 放入代理商ID 后付无代理商
			cfr.setAgentId(null);
			// 放入运营商类型
			cfr.setOperatorstype(stat.getOperatorstype());

			// 放入部门ID
			if (null == cfr.getBelongSale())
			{
				cfr.setDepartmentId(null);
			} else {
				Integer deptId = deptList.get(cfr.getBelongSale().toString());
				if (null == deptId)
				{
					User user = userService.getById(cfr.getBelongSale());
					if (user != null) {
						JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
						if (fistLevel != null)
							deptId = fistLevel.getDepartmentId();
					}

					// 将查询到的部门放入缓存中
					deptList.put(cfr.getBelongSale().toString(), null == deptId ? -1 : deptId);
					cfr.setDepartmentId(deptId);
				} else {
					cfr.setDepartmentId(deptId == -1 ? null: deptId);
				}
			}

			// 查询单价
			String unitPrice = unitPrices.get(cfr.getClientid());
			if (unitPrice == null) {
				Map<String, Object> queryPrice = new HashMap<>();
				queryPrice.put("date", statTime);
				queryPrice.put("clientId", cfr.getClientid());
				queryPrice.put("smsType", cfr.getSmstype());
				unitPrice = agentInfoService.getUserPriceByClientId(queryPrice);
				unitPrice = StringUtils.isBlank(unitPrice) ? "0" : unitPrice;
			}
			cfr.setUnitPrice(new BigDecimal(unitPrice));
			houfuClientFailList.add(cfr);
		}

		if (houfuClientFailList.size() > 0) {
			LOGGER.debug("客户失败返：OEM代理商后付费用户 批量插入返还列表 {}", JSON.toJSONString(houfuClientFailList));
			clientFailReturnService.insertBatch(houfuClientFailList);
		}

		LOGGER.debug("2. OEM后付费自动返还");
		// 4. OEM代理商后付费用户自动返还
		if (houfuClientFailList.size() > 0) {
			Map<String, Account> accounts = new HashMap<>();
			AgentBalanceBill agentBalanceBill;
			StringBuilder remark = new StringBuilder(); // 账单备注
			Map<String, Object> data = new HashedMap();

			// 将belong_sale合一处理
			Map<String, List<ClientFailReturn>> maps = new HashMap<>();
			for (ClientFailReturn failReturn : houfuClientFailList) {
				String key = failReturn.getClientid() + failReturn.getSmstype() + "";
				List<ClientFailReturn> list = maps.get(key);
				if (list == null) {
					list = new ArrayList<>();
					maps.put(key, list);
				}

				list.add(failReturn);
			}

			List<ClientFailReturn> newHoufuClientFailList = new ArrayList<>();
			ClientFailReturn clientFailReturn;
			for (String key : maps.keySet()) {
				clientFailReturn = null;
				List<ClientFailReturn> list = maps.get(key);
				for (ClientFailReturn failReturn : list) {

					if (clientFailReturn == null) {
						clientFailReturn = new ClientFailReturn();
						BeanUtil.copyProperties(failReturn, clientFailReturn);
						continue;
					}

					clientFailReturn
							.setSubmitsuccess(clientFailReturn.getSubmitsuccess() + failReturn.getSubmitsuccess());
					clientFailReturn.setSubmitfail(clientFailReturn.getSubmitfail() + failReturn.getSubmitfail());
					clientFailReturn.setReportfail(clientFailReturn.getReportfail() + failReturn.getReportfail());
					clientFailReturn.setReturnNumber(clientFailReturn.getReturnNumber() + failReturn.getReturnNumber());
				}

				newHoufuClientFailList.add(clientFailReturn);
			}

			for (ClientFailReturn failReturn : newHoufuClientFailList) {

				LOGGER.debug("【OEM后付费自动返还任务】客户{}开始返还", failReturn.getClientid());
				// 本次返还金额
				BigDecimal money = failReturn.getUnitPrice().multiply(new BigDecimal(failReturn.getReturnNumber()));

				params.clear();
				params.put("money", money);

				// 查询代理商ID用于返还费用
				Account account = accounts.get(failReturn.getClientid());
				if (account == null) {
					account = accountService.getByClientid(failReturn.getClientid());
				}

				if (account == null) {
					LOGGER.error("客户失败返：OEM代理商客户自动返还失败，clientId={}，未查询到该客户", failReturn.getClientid());
					throw new ClientFailReturnException(
							"客户失败返：OEM代理商客户自动返还失败，clientId=" + failReturn.getClientid() + "，未查询到该客户");
				}

				// **** 查询出代理商余额 要放到返还费用之前
				String balance = agentInfoService.getAgentBalanceByAgentId(account.getAgentId().toString());

				// 对代理商进行返还费用
				/*
				 * params.put("agentId", Integer.valueOf(account.getAgentId()));
				 * 
				 * LOGGER.debug("客户失败返：OEM代理商后付费用户自动返还 更新代理商余额 参数{}",
				 * JSON.toJSONString(params));
				 * agentInfoService.addAgentAccountBalance(params);
				 * 
				 * // 增加代理商入账 // 增加短信账单 agentBalanceBill = new
				 * AgentBalanceBill(); agentBalanceBill.setAdminId(0l);
				 * agentBalanceBill.setAgentId(Integer.valueOf(account.
				 * getAgentId())); agentBalanceBill.setAmount(money);
				 * 
				 * 
				 * 
				 * // 算出加上本次后的余额
				 * agentBalanceBill.setBalance(MathUtils.roundDown(new
				 * BigDecimal(balance).add(money), 4));
				 * agentBalanceBill.setCreateTime(now);
				 * agentBalanceBill.setFinancialType("0");
				 * agentBalanceBill.setOrderId(null);
				 * agentBalanceBill.setClientId(failReturn.getClientid());
				 * agentBalanceBill.setPaymentType(PaymentType.后付费客户失败返还.
				 * getValue());
				 */

				remark.setLength(0);
				remark.append(failReturn.getClientid());
				remark.append("/");
				remark.append(account.getName());
				remark.append("/");
				remark.append(SmsTypeEnum.getDescByValue(failReturn.getSmstype()));
				remark.append("/");
				// 单价
				remark.append(FmtUtils.hold4Decimal(failReturn.getUnitPrice()));
				remark.append("元");
				remark.append("/");
				remark.append(failReturn.getReturnNumber());
				remark.append("条");
				remark.append("/返还");
				remark.append(statTimeDesc);

				/*
				 * agentBalanceBill.setRemark(remark.toString());
				 * LOGGER.debug("客户失败返：OEM代理商后付费用户自动返还 插入余额账单 {}",
				 * JSON.toJSONString(agentBalanceBill));
				 * agentInfoService.addAgentBalanceBill(agentBalanceBill);
				 */

				// data=this.agentUpdateByBalanceOP(account.getAgentId(),failReturn.getClientid(),money,remark.toString());

				LOGGER.debug("-----------------开始返还入库-----------------------");
				data = jsmsSaleCreditService.agentUpdateByBalanceOP(CreditRemarkType.返还.getValue(), SysConstant.SYS_ID,
						account.getAgentId(), failReturn.getClientid(), money, remark.toString());

				LOGGER.debug("【OEM后付费自动返还任务】客户失败返结果:{}", JsonUtils.toJson(data));

				if (Objects.equals(SysConstant.FAIL, data.get("status"))) {
					LOGGER.error("【客户失败返任务】客户失败返：OEM代理商客户自动返还,原因:{},代理商ID:{}", JsonUtils.toJson(data),
							account.getAgentId());
					continue;
				}

			}
		}

		LOGGER.debug("客户失败返：完成任务 {}");
	}

	/*
	 * 授信改造 代理商相关变化
	 * 
	 * @param
	 * 
	 * @param
	 * 
	 * @return private Map<String,Object> agentUpdateByBalanceOP(Integer
	 * agnetId,String clientId, BigDecimal money,String remark){
	 * Map<String,Object> data=new HashedMap();
	 * 
	 * JsmsAgentAccount agentAcc=agentAccountMapper.getByAgentId(agnetId);
	 * if(agentAcc==null){ data.put("status","fail"); data.put("msg","代理商不存在！");
	 * return data; }
	 * 
	 * JsmsAgentAccount agentAccount=new JsmsAgentAccount();
	 * agentAccount.setAgentId(agentAcc.getAgentId()); JsmsAgentBalanceBill
	 * bill=new JsmsAgentBalanceBill(); bill.setAgentId(agentAcc.getAgentId());
	 * bill.setAdminId(0L);
	 * 
	 * BigDecimal backPay=BigDecimal.ZERO;//回款金额
	 * 
	 * if(agentAcc.getBalance().compareTo(BigDecimal.ZERO)==-1){
	 * 
	 * if(agentAcc.getNoBackPayment().compareTo(money)==-1){ //充值金额大于欠款
	 * agentAccount.setNoBackPayment(BigDecimal.ZERO);
	 * backPay=agentAcc.getNoBackPayment(); }else {
	 * 
	 * agentAccount.setNoBackPayment(agentAcc.getNoBackPayment().subtract(money)
	 * ); backPay=money; }
	 * agentAccount.setBalance(agentAcc.getBalance().add(money));
	 * agentAccount.setHistoryPayment(backPay);
	 * bill.setNoBackPayment(agentAccount.getNoBackPayment());
	 * bill.setHistoryPayment(agentAccount.getHistoryPayment()); }else {
	 * agentAccount.setBalance(agentAcc.getBalance().add(money));
	 * 
	 * // bill.setNoBackPayment(agentAcc.getNoBackPayment()); //
	 * bill.setCurrentCredit(agentAcc.getCurrentCredit());
	 * bill.setNoBackPayment(agentAcc.getNoBackPayment());
	 * bill.setHistoryPayment(agentAcc.getHistoryPayment()); }
	 * bill.setCurrentCredit(agentAcc.getCurrentCredit());
	 * bill.setCreditBalance(agentAcc.getCreditBalance());
	 * bill.setPaymentType(PaymentType.后付费客户失败返还.getValue());
	 * bill.setFinancialType(FinancialType.入账.getValue());
	 * agentAccount.setAccumulatedConsume(agentAcc.getAccumulatedConsume().
	 * subtract(money)); bill.setAmount(money);
	 * bill.setBalance(agentAccount.getBalance()); bill.setRemark(remark);
	 * bill.setOrderId(null); bill.setClientId(clientId); bill.setCreateTime(new
	 * Date()); int binsert=agentBalanceBillMapper.insert(bill); if(binsert>0){
	 * int accUpdate=agentAccountMapper.updateSelective(agentAccount);
	 * if(accUpdate<=0){ data.put("status", "fail"); data.put("msg",
	 * "更新代理商账号金额失败！"); return data; } }else { data.put("status", "fail");
	 * data.put("msg", "生成代理商账单失败！"); return data; }
	 * if(backPay.compareTo(BigDecimal.ZERO)!=0){
	 * 
	 * data=this.creditForSale(backPay,agnetId);
	 * if(Objects.equals(SysConstant.FAIL,data.get("status"))){ return data; } }
	 * data.put("status", "success"); data.put("msg", "更新代理商余额成功！"); return
	 * data; }
	 * 
	 * /** 回款销售变化
	 * 
	 * @param
	 * 
	 * @param
	 * 
	 * @return
	 */
	/*
	 * private Map<String,Object> creditForSale(BigDecimal backPayMoney,Integer
	 * agentId){ //,String agentId,String amount,String operateType
	 * Map<String,Object> results=new HashedMap();
	 * 
	 * JsmsAgentInfo agent=jsmsAgentInfoMapper.getByAgentId(agentId);
	 * if(agent==null){ results.put("status","fail");
	 * results.put("msg","代理商不存在！"); return results; }else {
	 * 
	 * JsmsSaleCreditAccount
	 * saleAccount=jsmsSaleCreditAccountMapper.getBySaleId(agent.getBelongSale()
	 * ); if(saleAccount==null){ results.put("status","fail");
	 * results.put("msg","销售账户信息不存在！"); return results; }else {
	 * 
	 * JsmsSaleCreditBill bill=new JsmsSaleCreditBill();
	 * 
	 * 
	 * 
	 * JsmsSaleCreditAccount saleAcc=new JsmsSaleCreditAccount();
	 * saleAcc.setSaleId(saleAccount.getSaleId()); //添加授信
	 * bill.setBusinessType(BusinessType.客户回款.getValue());
	 * bill.setFinancialType(FinancialType.入账.getValue());
	 * 
	 * 
	 * saleAcc.setNoBackPayment(saleAccount.getNoBackPayment().subtract(
	 * backPayMoney));
	 * saleAcc.setAgentHistoryPayment(saleAccount.getAgentHistoryPayment().add(
	 * backPayMoney));
	 * 
	 * bill.setSaleId(saleAccount.getSaleId()); bill.setAmount(backPayMoney);
	 * bill.setObjectId(Long.valueOf(agentId));
	 * bill.setObjectType(ObjectType.代理商.getValue());
	 * bill.setFinancialHistoryCredit(saleAccount.getFinancialHistoryCredit());
	 * bill.setCurrentCredit(saleAccount.getCurrentCredit());
	 * bill.setSaleHistoryCredit(saleAccount.getSaleHistoryCredit());
	 * bill.setAgentHistoryPayment(saleAcc.getAgentHistoryPayment());
	 * bill.setNoBackPayment(saleAcc.getNoBackPayment()); bill.setAdminId(0L);
	 * bill.setCreateTime(new Date()); bill.setRemark("后付费客户失败返还-回款");
	 * 
	 * //生成账单信息 int abi=jsmsSaleCreditBillMapper.insert(bill); if(abi>0){
	 * LOGGER.info("生成销售账单信息成功,账单bill={}", JsonUtils.toJson(bill)); //更新账户信息
	 * 
	 * int aup=jsmsSaleCreditAccountMapper.updateSelective(saleAcc); if(aup>0){
	 * LOGGER.info("更新销售账户信息成功,agentAccount={}", JsonUtils.toJson(saleAcc));
	 * results.put("status","success"); results.put("msg","更新销售账户信息成功！");
	 * 
	 * }else { results.put("status","fail"); results.put("msg","更新销售账户信息失败!");
	 * LOGGER.error("更新销售账户信息失败,agentAccount={}", JsonUtils.toJson(saleAcc));
	 * return results; }
	 * 
	 * 
	 * }else { results.put("status","fail"); results.put("msg","生成销售账单信息入库失败!");
	 * LOGGER.error("生成销售账单信息入库失败,账单bill={}", JsonUtils.toJson(bill)); return
	 * results; }
	 * 
	 * 
	 * }
	 * 
	 * } results.put("status","success"); results.put("msg","销售账单信息更新入库成功!");
	 * 
	 * return results; }
	 */

	@Override
	public void fixStaticClientFailReturn(DateTime statDay) {
		String dealTime = statDay.toString("yyyyMMdd");
		LOGGER.debug("【修复客户失败返返清单表任务】开始 表=t_sms_client_fail_return, 统计日期={}", dealTime);
		Date now = DateTime.now().toDate();

		// 方案一
		// 修复归属销售
		// fixBelongSale();
		//
		// // 修复归属代理商
		// fixAgentId();
		//
		// // 修复部门ID
		// fixAgentId();
		//
		// // 修复运营商类型
		// fixOperatorstype();
		//
		// fixDepartmentId();
		
		// 方案二
		// 查询起始修复时间
		String startDay = clientFailReturnService.getStartFixDay();
		if (StringUtils.isBlank(startDay))
		{
			LOGGER.debug("【修复客户失败返返清单表任务】完成，起始修复天数查询为空");
			return;
		}

		String endDay = clientFailReturnService.getEndFixDay();
		if (StringUtils.isBlank(endDay))
		{
			LOGGER.debug("【修复客户失败返返清单表任务】完成，结束修复天数查询为空");
			return;
		}

		DateTime startTime = UcpaasDateUtils.parseDate(startDay, "yyyyMMdd");
		DateTime endTime = UcpaasDateUtils.parseDate(endDay, "yyyyMMdd");

		// 起始修复时间在当前时间之后，退出修复
		if (startTime.isAfterNow())
		{
			LOGGER.debug("【修复客户失败返返清单表任务】完成，起始修复天数大于当前时间");
			return;
		}

		// 查询出所有的已返还条数的客户信息
		List<ClientFailReturn> clientFailReturns = clientFailReturnService.findHasReturnGroupByDay();
		LOGGER.debug("【修复客户失败返返清单表任务】 当前已返还客户按天统计数据{}", JSON.toJSONString(clientFailReturns));

		// 查询所有的OEM代理商
		List<String> oemAgentIds = agentInfoService.findOemAgentIdList();
		// 部门缓存
		Map<String, Integer> deptList = new HashMap<>();

		String day;
		endTime = endTime.plusDays(1);
		while (startTime.isBefore(endTime))
		{
			// 开始处理当天的数据
			day = startTime.toString("yyyyMMdd");

			LOGGER.debug("【修复客户失败返返清单表任务】开始修复 {} 的数据", day);

			// 1. 删除当天的统计信息
			clientFailReturnService.deleteByDate(Integer.parseInt(day));

			// 2. 品牌、销售、OEM代理商预付费用户的基础表数据
			List<AccessChannelStatistics> yufuList = clientFailReturnService.findYuFuForClientFailReturnByDay(Integer.parseInt(day));
			LOGGER.debug("【修复客户失败返返清单表任务】开始修复 {} 的数据，基础统计表数据 {}", day, JSON.toJSONString(yufuList));

			if (!Collections3.isEmpty(yufuList)) {
				List<ClientFailReturn> yufuClientFailList = new ArrayList<>();
				ClientFailReturn cfr;
				for (AccessChannelStatistics stat : yufuList) {
					// 返还条数为0的时候不需要插入
					if (stat.getReturnTotalNumber() == null || stat.getReturnTotalNumber() <= 0) {
						continue;
					}

					cfr = new ClientFailReturn();
					cfr.setClientid(stat.getClientid());
					cfr.setPaytype(stat.getPaytype());
					cfr.setChargeRule(stat.getChargeRule());
					cfr.setSmstype(stat.getSmstype());
					cfr.setSubId(stat.getSubId());
					cfr.setSubmitsuccess(stat.getSubmitsuccess());
					cfr.setSubmitfail(stat.getSubmitfail());
					cfr.setReportfail(stat.getReportfail());
					cfr.setReturnNumber(stat.getReturnTotalNumber());
					cfr.setDate(Integer.parseInt(day));
					cfr.setCreateTime(now);
					cfr.setBelongSale(stat.getBelongSale());

					// 放入代理商ID
					cfr.setAgentId(stat.getAgentId());
					// 放入运营商类型
					cfr.setOperatorstype(stat.getOperatorstype());

					// 放入部门ID
					if (null == cfr.getBelongSale())
					{
						cfr.setDepartmentId(null);
					} else {
						Integer deptId = deptList.get(cfr.getBelongSale().toString());
						if (null == deptId)
						{
							User user = userService.getById(cfr.getBelongSale());
							if (user != null) {
								JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
								if (fistLevel != null)
									deptId = fistLevel.getDepartmentId();
							}

							// 将查询到的部门放入缓存中
							deptList.put(cfr.getBelongSale().toString(), null == deptId ? -1 : deptId);
							cfr.setDepartmentId(deptId);
						} else {
							cfr.setDepartmentId(deptId == -1 ? null: deptId);
						}
					}

					if (oemAgentIds.contains(stat.getAgentId().toString())) {
						// 查询代理商池
						JsmsOemClientPool oemClientPool = jsmsOemClientPoolService
								.getByClientPoolId(Long.parseLong(cfr.getSubId()));
						if (oemClientPool == null) {
							LOGGER.error("客户失败返：OEM代理商客户根据SUBID查询客户池为空，clientId={}，sub_id={}", cfr.getClientid(),
									cfr.getSubId());
							throw new ClientFailReturnException("客户失败返：OEM代理商客户根据SUBID查询客户池为空，clientId=" + cfr.getClientid()
									+ "，sub_id=" + cfr.getSubId());
						}

						// 设置值
						cfr.setProductType(oemClientPool.getProductType());
						cfr.setOperatorCode(oemClientPool.getOperatorCode());
						cfr.setAreaCode(oemClientPool.getAreaCode());
						cfr.setDueTime(oemClientPool.getDueTime());
						cfr.setUnitPrice(oemClientPool.getUnitPrice());
					} else {
						// 查询客户订单
						JsmsClientOrder clientOrder = jsmsClientOrderService.getBySubId(Long.parseLong(cfr.getSubId()));
						if (clientOrder == null) {
							LOGGER.error("客户失败返：品牌代理商客户根据SUBID查询客户订单为空，clientId={}，sub_id={}", cfr.getClientid(),
									cfr.getSubId());
							throw new ClientFailReturnException("客户失败返：品牌代理商客户根据SUBID查询客户订单为空，clientId=" + cfr.getClientid()
									+ "，sub_id=" + cfr.getSubId());
						}

						// 设置值
						cfr.setProductType(clientOrder.getProductType());
						cfr.setOperatorCode(clientOrder.getOperatorCode());
						cfr.setAreaCode(clientOrder.getAreaCode());
						cfr.setDueTime(clientOrder.getEndTime());
						cfr.setUnitPrice(clientOrder.getUnitPrice());
					}

					// 判断是否已返还，设置状态
					ClientFailReturn hasReturn = null;
					for (ClientFailReturn clientFailReturn : clientFailReturns) {
						if (clientFailReturn.getClientid().equals(cfr.getClientid()) && clientFailReturn.getDate().toString().equals(day))
						{
							hasReturn = clientFailReturn;
							break;
						}
					}

					if (null == hasReturn || hasReturn.getReturnNumber() <= 0)
					{
						cfr.setRefundState(0);
					} else {
						// 本身的返还条数 小于等于 已返还条数
						if (cfr.getReturnNumber().intValue() <= hasReturn.getReturnNumber().intValue())
						{
							cfr.setRefundState(1);
							// 将客户的已返还条数减去本次的返还条数
							hasReturn.setReturnNumber(hasReturn.getReturnNumber() - cfr.getReturnNumber());
						} else {
							// 本次不处理
							cfr.setRefundState(0);
							LOGGER.error("【修复客户失败返返清单表任务】 本条数据修复失败， 待返还条数大于已返还条数， 已返还条数 {}  返还记录 {}",
									JSON.toJSONString(hasReturn.getReturnNumber()), JSON.toJSONString(cfr));
						}
					}

					yufuClientFailList.add(cfr);
				}

				if (yufuClientFailList.size() > 0) {
					LOGGER.debug("客户失败返：品牌、销售、OEM代理商预付费 批量插入返还列表 {}", JSON.toJSONString(yufuClientFailList));
					clientFailReturnService.insertBatch(yufuClientFailList);
				}
			}

			// 4. OEM代理商后付费用户
			List<AccessChannelStatistics> oemHouFuList = clientFailReturnService.findOemHouFuForClientFailReturnByDay(Integer.parseInt(day), oemAgentIds);
			LOGGER.debug("客户失败返：OEM代理商后付费用户ACCESS统计列表 {}", JSON.toJSONString(oemHouFuList));
			if (!Collections3.isEmpty(oemHouFuList)) {

				List<ClientFailReturn> houfuClientFailList = new ArrayList<>();
				Map<String, String> unitPrices = new HashMap<>();
				ClientFailReturn cfr;
				for (AccessChannelStatistics stat : oemHouFuList) {
					// 返还条数为0的时候不需要插入
					if (stat.getReturnTotalNumber() == null || stat.getReturnTotalNumber() <= 0) {
						continue;
					}
					cfr = new ClientFailReturn();
					cfr.setClientid(stat.getClientid());
					cfr.setPaytype(stat.getPaytype());
					cfr.setChargeRule(stat.getChargeRule());
					cfr.setSmstype(stat.getSmstype());
					cfr.setSubId(null);
					cfr.setSubmitsuccess(stat.getSubmitsuccess());
					cfr.setSubmitfail(stat.getSubmitfail());
					cfr.setReportfail(stat.getReportfail());
					cfr.setReturnNumber(stat.getReturnTotalNumber());
					cfr.setRefundState(1); // OEM后付费后面直接返还
					cfr.setDate(Integer.parseInt(day));
					cfr.setCreateTime(now);
					cfr.setBelongSale(stat.getBelongSale());
					cfr.setProductType(null);
					cfr.setOperatorCode(null);
					cfr.setAreaCode(null);
					cfr.setDueTime(null);

					// 放入代理商ID 后付无代理商
					cfr.setAgentId(null);
					// 放入运营商类型
					cfr.setOperatorstype(stat.getOperatorstype());

					// 放入部门ID
					if (null == cfr.getBelongSale())
					{
						cfr.setDepartmentId(null);
					} else {
						Integer deptId = deptList.get(cfr.getBelongSale().toString());
						if (null == deptId)
						{
							User user = userService.getById(cfr.getBelongSale());
							if (user != null) {
								JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
								if (fistLevel != null)
									deptId = fistLevel.getDepartmentId();
							}

							// 将查询到的部门放入缓存中
							deptList.put(cfr.getBelongSale().toString(), null == deptId ? -1 : deptId);
							cfr.setDepartmentId(deptId);
						} else {
							cfr.setDepartmentId(deptId == -1 ? null: deptId);
						}
					}

					// 查询单价
					String unitPrice = unitPrices.get(cfr.getClientid());
					if (unitPrice == null) {
						Map<String, Object> queryPrice = new HashMap<>();
						queryPrice.put("date", day);
						queryPrice.put("clientId", cfr.getClientid());
						queryPrice.put("smsType", cfr.getSmstype());
						unitPrice = agentInfoService.getUserPriceByClientId(queryPrice);
						unitPrice = StringUtils.isBlank(unitPrice) ? "0" : unitPrice;
					}
					cfr.setUnitPrice(new BigDecimal(unitPrice));
					houfuClientFailList.add(cfr);
				}

				if (houfuClientFailList.size() > 0) {
					LOGGER.debug("客户失败返：OEM代理商后付费用户 批量插入返还列表 {}", JSON.toJSONString(houfuClientFailList));
					clientFailReturnService.insertBatch(houfuClientFailList);
				}
			}

			// 天数+1
			startTime = startTime.plusDays(1);
		}

		LOGGER.debug("【修复客户失败返返清单表任务】完成");
	}


	private Map<Integer, Map<String, List<ClientFailReturn>>> buildClientFailListToMapEveryDay(
			List<ClientFailReturn> clientFailReturns) {
		Map<Integer, Map<String, List<ClientFailReturn>>> maps = new TreeMap<>();
		for (ClientFailReturn failReturn : clientFailReturns) {
			Integer date = failReturn.getDate();

			// 将每天的放入一个KEY
			Map<String, List<ClientFailReturn>> subMaps = maps.get(date);
			if (subMaps == null || subMaps.isEmpty()) {
				subMaps = new HashMap<>();
				maps.put(date, subMaps);
			}

			// 根据客户分开存放
			List<ClientFailReturn> list = subMaps.get(failReturn.getClientid());
			if (list == null) {
				list = new ArrayList<>();
				list.add(failReturn);
				subMaps.put(failReturn.getClientid(), list);
			} else {
				list.add(failReturn);
			}
		}
		return maps;
	}

	private void fixBelongSale() {
		LOGGER.debug("【修复客户失败返返清单表任务】 开始修复归属销售 {}", System.currentTimeMillis());

		// 1. 查询失败返还数据
		ClientFailReturn clientFailReturn = new ClientFailReturn();
		clientFailReturn.setFixBelongSale(1);
		List<ClientFailReturn> clientFailReturns = clientFailReturnService.findList(new ClientFailReturn());
		if (Collections3.isEmpty(clientFailReturns)) {
			LOGGER.debug("【修复客户失败返返清单表任务】 结束修复归属销售，未查询到需要修复的失败返还记录 {}", System.currentTimeMillis());
			return;
		}

		// 2. 重新构造数据，一天一天构造
		Map<Integer, Map<String, List<ClientFailReturn>>> maps = buildClientFailListToMapEveryDay(clientFailReturns);

		int count;
		List<ClientFailReturn> batchUpdateList = new ArrayList<>();

		// 循环处理每天的
		for (Integer date : maps.keySet()) {
			Map<String, List<ClientFailReturn>> subMaps = maps.get(date);
			if (subMaps == null || subMaps.isEmpty()) {
				continue;
			}

			batchUpdateList.clear();
			for (String clientid : subMaps.keySet()) {

				// 需要修复的数据
				List<ClientFailReturn> list = subMaps.get(clientid);
				if (Collections3.isEmpty(list)) {
					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN BelongSale FIX DATE={} clientid={} done，失败返还记录为空", date,
							clientid);
					continue;
				}

				LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX BelongSale DATE={} clientid={}", date, clientid);
				AccessChannelStatistics stat = accessChannelStatisticsService
						.getTopSendBelongSaleByClientidAndDate(clientid, date);
				if (stat == null || stat.getBelongSale() == null) {
					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX BelongSale DATE={} clientid={} error，未查询到access统计表中的归属销售",
							date, clientid);
					continue;
				}

				for (ClientFailReturn failReturn : list) {
					failReturn.setId(failReturn.getId());
					failReturn.setBelongSale(stat.getBelongSale());
					batchUpdateList.add(failReturn);
				}
			}

			count = clientFailReturnService.updateBatchBelongSale(batchUpdateList);
			LOGGER.debug("【修复客户失败返返清单表任务】，BEGIN FIX BelongSale DATE={} updateCount={} Record={}", date, count,
					JSON.toJSONString(batchUpdateList));
		}

		LOGGER.debug("【修复客户失败返返清单表任务】 结束修复归属销售 {}", System.currentTimeMillis());
	}

	// 修复归属代理商
	private void fixAgentId() {
		LOGGER.debug("【修复客户失败返返清单表任务】 开始修复归属代理商 {}", System.currentTimeMillis());

		// 1. 查询失败返还数据
		ClientFailReturn clientFailReturn = new ClientFailReturn();
		clientFailReturn.setFixAgentId(1);
		List<ClientFailReturn> clientFailReturns = clientFailReturnService.findList(new ClientFailReturn());
		if (Collections3.isEmpty(clientFailReturns)) {
			LOGGER.debug("【修复客户失败返返清单表任务】 结束修复归属代理商，未查询到需要修复的失败返还记录 {}", System.currentTimeMillis());
			return;
		}

		// 2. 重新构造数据，一天一天构造
		Map<Integer, Map<String, List<ClientFailReturn>>> maps = buildClientFailListToMapEveryDay(clientFailReturns);

		int count;
		List<ClientFailReturn> batchUpdateList = new ArrayList<>();

		// 循环处理每天的
		for (Integer date : maps.keySet()) {
			Map<String, List<ClientFailReturn>> subMaps = maps.get(date);
			if (subMaps == null || subMaps.isEmpty()) {
				continue;
			}

			batchUpdateList.clear();
			for (String clientid : subMaps.keySet()) {

				// 需要修复的数据
				List<ClientFailReturn> list = subMaps.get(clientid);
				if (Collections3.isEmpty(list)) {
					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} done，失败返还记录为空", date, clientid);
				}

				for (ClientFailReturn failReturn : list) {
					if (null == failReturn.getBelongSale()) {
						LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} done，失败返还记录的归属销售为空，跳过修复代理商",
								date, clientid);
						continue;
					}

					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={}", date, clientid);
					String agentId = accessChannelStatisticsService.getTopSendAgentIdByClientidAndDateAndBelongSale(
							clientid, date, failReturn.getBelongSale());
					if (StringUtils.isBlank(agentId)) {
						LOGGER.debug(
								"【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} belongSale={} error，未查询到access统计表中的归属代理商",
								date, clientid, failReturn.getBelongSale());
						continue;
					}

					failReturn.setId(failReturn.getId());
					failReturn.setAgentId(Integer.parseInt(agentId));
					batchUpdateList.add(failReturn);
				}
			}

			if (batchUpdateList.size() == 0)
			{
				LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} 可修復數據為空", date);
				continue;
			}
			count = clientFailReturnService.updateBatchAgentId(batchUpdateList);
			LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} updateCount={} Record={}", date, count,
					JSON.toJSONString(batchUpdateList));
		}

		LOGGER.debug("【修复客户失败返返清单表任务】 结束修复归属销售 {}", System.currentTimeMillis());
	}

	// 修复部门ID
	private void fixDepartmentId() {
		LOGGER.debug("【修复客户失败返返清单表任务】 开始修复部门Id {}", System.currentTimeMillis());

		// 1. 查询失败返还数据
		ClientFailReturn clientFailReturn = new ClientFailReturn();
		clientFailReturn.setFixDepartmentId(1);
		List<ClientFailReturn> clientFailReturns = clientFailReturnService.findList(new ClientFailReturn());
		if (Collections3.isEmpty(clientFailReturns)) {
			LOGGER.debug("【修复客户失败返返清单表任务】 结束修复部门Id {}", System.currentTimeMillis());
			return;
		}

		// 2. 重新构造数据，一天一天构造
		Map<Integer, Map<String, List<ClientFailReturn>>> maps = buildClientFailListToMapEveryDay(clientFailReturns);

		int count;
		List<ClientFailReturn> batchUpdateList = new ArrayList<>();

		// 循环处理每天的
		for (Integer date : maps.keySet()) {
			Map<String, List<ClientFailReturn>> subMaps = maps.get(date);
			if (subMaps == null || subMaps.isEmpty()) {
				continue;
			}

			batchUpdateList.clear();
			for (String clientid : subMaps.keySet()) {

				// 需要修复的数据
				List<ClientFailReturn> list = subMaps.get(clientid);
				if (Collections3.isEmpty(list)) {
					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX DeptId DATE={} clientid={} done，失败返还记录为空", date, clientid);
					continue;
				}

				for (ClientFailReturn failReturn : list) {
					if (null == failReturn.getBelongSale()) {
						LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX DeptId DATE={} clientid={} done，失败返还记录的归属销售为空，跳过修复部门Id",
								date, clientid);
						continue;
					}

					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX DeptId DATE={} clientid={}", date, clientid);

					Integer departmentId = null;
					User user = userService.getById(failReturn.getBelongSale());
					if (user != null) {
						JsmsDepartment fistLevel = jsmsDepartmentService.getFistLevelDeparment(user.getDepartmentId());
						if (fistLevel != null)
							departmentId = fistLevel.getDepartmentId();
					}

					if (departmentId == null) {
						LOGGER.debug(
								"【修复客户失败返返清单表任务】 BEGIN FIX DeptId DATE={} clientid={} belongSale={} done，未获取到，跳过修复部门Id",
								date, clientid, failReturn.getBelongSale());
						continue;
					}

					failReturn.setId(failReturn.getId());
					failReturn.setDepartmentId(departmentId);
					batchUpdateList.add(failReturn);
				}
			}

			if (batchUpdateList.size() == 0)
			{
				LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} 可修復數據為空", date);
				continue;
			}

			count = clientFailReturnService.updateBatchDepartmentId(batchUpdateList);
			LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX DeptId DATE={} updateCount={} Record={}", date, count,
					JSON.toJSONString(batchUpdateList));
		}

		LOGGER.debug("【修复客户失败返返清单表任务】 结束修复部门Id {}", System.currentTimeMillis());
	}

	// 修复运营商类型
	private void fixOperatorstype() {
		LOGGER.debug("【修复客户失败返返清单表任务】 开始修复通道运营商类型 {}", System.currentTimeMillis());

		// 1. 查询失败返还数据
		ClientFailReturn clientFailReturn = new ClientFailReturn();
		clientFailReturn.setFixOperatorstype(1);
		List<ClientFailReturn> clientFailReturns = clientFailReturnService.findList(new ClientFailReturn());
		if (Collections3.isEmpty(clientFailReturns)) {
			LOGGER.debug("【修复客户失败返返清单表任务】 结束修复通道运营商类型，未查询到需要修复的失败返还记录 {}", System.currentTimeMillis());
			return;
		}

		// 2. 重新构造数据，一天一天构造
		Map<Integer, Map<String, List<ClientFailReturn>>> maps = buildClientFailListToMapEveryDay(clientFailReturns);

		int count;
		List<ClientFailReturn> batchUpdateList = new ArrayList<>();

		// 循环处理每天的
		for (Integer date : maps.keySet()) {
			Map<String, List<ClientFailReturn>> subMaps = maps.get(date);
			if (subMaps == null || subMaps.isEmpty()) {
				continue;
			}

			batchUpdateList.clear();
			for (String clientid : subMaps.keySet()) {

				// 需要修复的数据
				List<ClientFailReturn> list = subMaps.get(clientid);
				if (Collections3.isEmpty(list)) {
					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} clientid={} done，失败返还记录为空", date,
							clientid);
					continue;
				}

				for (ClientFailReturn failReturn : list) {
					if (null == failReturn.getBelongSale()) {
						LOGGER.debug(
								"【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} clientid={} done，失败返还记录的归属销售为空，跳过修复通道运营商类型",
								date, clientid);
						continue;
					}

					if (null == failReturn.getAgentId()) {
						LOGGER.debug(
								"【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} clientid={} done，失败返还记录的归属代理商为空，跳过修复代理商通道运营商类型",
								date, clientid);
						continue;
					}

					LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} clientid={}", date, clientid);
					String operatorstype = accessChannelStatisticsService
							.getTopSendOperatorstypeByClientidAndDateAndBelongSaleAndAgentId(clientid, date,
									failReturn.getBelongSale(), failReturn.getAgentId());
					if (StringUtils.isBlank(operatorstype)) {
						LOGGER.debug(
								"【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} clientid={} belongSale={} agentId={} error，未查询到access统计表中的运营商类型",
								date, clientid, failReturn.getBelongSale(), failReturn.getAgentId());
						continue;
					}

					failReturn.setId(failReturn.getId());
					failReturn.setOperatorstype(Integer.parseInt(operatorstype));
					batchUpdateList.add(failReturn);
				}
			}

			if (batchUpdateList.size() == 0)
			{
				LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX AgentId DATE={} clientid={} 可修復數據為空", date);
				continue;
			}

			count = clientFailReturnService.updateBatchOperatorstype(batchUpdateList);
			LOGGER.debug("【修复客户失败返返清单表任务】 BEGIN FIX OperatorsType DATE={} updateCount={} Record={}", date, count,
					JSON.toJSONString(batchUpdateList));
		}

		LOGGER.debug("【修复客户失败返返清单表任务】 结束修复通道运营商类型 {}", System.currentTimeMillis());
	}

}
