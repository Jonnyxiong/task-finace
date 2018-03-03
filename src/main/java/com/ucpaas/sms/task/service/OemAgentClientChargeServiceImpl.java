package com.ucpaas.sms.task.service;

import com.jsmsframework.common.enums.*;
import com.jsmsframework.sale.credit.constant.SysConstant;
import com.jsmsframework.sale.credit.service.JsmsSaleCreditService;
import com.ucpaas.sms.common.util.DateUtils;
import com.ucpaas.sms.common.util.FmtUtils;
import com.ucpaas.sms.task.entity.access.AccessChannelStatistics;
import com.ucpaas.sms.task.entity.message.AgentBalanceBill;
import com.ucpaas.sms.task.mapper.access.AccessChannelStatisticsMapper;
import com.ucpaas.sms.task.mapper.message.AgentInfoMapper;
import com.ucpaas.sms.task.model.TaskInfo;
import com.ucpaas.sms.task.util.JsonUtils;
import com.ucpaas.sms.task.util.UcpaasDateUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * OEM代理商计费
 * 
 */
@Service
public class OemAgentClientChargeServiceImpl implements OemAgentClientChargeService {

	@Autowired
	private AccessChannelStatisticsMapper accessChannelStatisticsMapper;

	@Autowired
	private AgentInfoMapper agentInfoMapper;

	@Autowired
	private JsmsSaleCreditService jsmsSaleCreditService;

	private static final Logger logger = LoggerFactory.getLogger("OemAgentClientChargeService");

	/**
	 * 后付费计费
	 * 
	 * @return 是否成功
	 */
	@Transactional("message")
	@Override
	public boolean houfufeiCharge(TaskInfo taskInfo) {
		Calendar begin = Calendar.getInstance();
		logger.debug("【每日OEM代理商后付费扣费任务】开始 = {}", DateUtils.formatDateTime(begin.getTime()));

		// Mod by lpjLiu 2017-10-11 将后付费扣费，设置成根据任务时间来往前推一天算出昨日的
		// 获取昨日
		// Calendar zuori = Calendar.getInstance();
		// zuori.set(Calendar.DATE, begin.get(Calendar.DATE) - 1);
		// String date = DateUtils.formatDate(zuori.getTime(), "yyyyMMdd");

		String format = taskInfo.getExecuteType().getFormat();
		DateTime executeNext = UcpaasDateUtils.parseDate(taskInfo.getExecuteNext(), format);
		DateTime statDay = executeNext.minusDays(1);
		String date = statDay.toString("yyyyMMdd");
		String statTimeDesc = statDay.getMonthOfYear() + "-" + statDay.getDayOfMonth();

		DateTime finishDate = executeNext.plusDays(1);

		// 检查access统计是否完成
		int accessChannelTask = agentInfoMapper.checkAccessChannelStatDone(finishDate.toString("yyyyMMdd"));
		if (accessChannelTask == 0) {
			logger.debug("【每日OEM代理商后付费扣费任务】每日创建ACCESS流水表(昨日数据)任务正在运行或还未运行，等待其完成...");
			return false;
		}

		// Add by lpjLiu 2017-10-13 查询余额账单判断是否扣费了，扣费了不重复跑
		int count = agentInfoMapper.checkOemHouFuIsRunByDate(executeNext.toString("yyyyMMdd"));
		if (count > 0){
			logger.debug("【每日OEM代理商后付费扣费任务】 {} 已经扣过费，本次任务不再处理", executeNext.toString("yyyyMMdd"));
			return true;
		}

		// 查询昨日的统计数据
		List<AccessChannelStatistics> statList = accessChannelStatisticsMapper
				.findZuoriHoufufeiClientList(Integer.parseInt(date));
		if (statList == null || statList.size() <= 0) {
			logger.debug("【每日OEM代理商后付费扣费任务】结束(未查询到access昨日{}统计记录) = {}", date,
					DateUtils.formatDateTime(Calendar.getInstance().getTime()));
			return true;
		}

		// 查询OEM代理商
		Set<String> ids = new HashSet<>();
		for (AccessChannelStatistics item : statList) {
			if (ids.contains(item.getAgentId().toString())) {
				continue;
			}
			ids.add(item.getAgentId().toString());
		}

		List<String> agentIds = agentInfoMapper.findOemAgentInfoByAgentIds(ids);
		if (agentIds == null || agentIds.size() < 0) {
			Calendar end = Calendar.getInstance();
			logger.debug("【每日OEM代理商后付费扣费任务】结束(查询到access昨日统计记录不存在OEM代理商) = {}", DateUtils.formatDateTime(end.getTime()));
			return true;
		}

		// 移除非OEM代理商的统计项
		for (Iterator<AccessChannelStatistics> iterator = statList.iterator(); iterator.hasNext();) {
			AccessChannelStatistics item = iterator.next();
			if (!agentIds.contains(item.getAgentId().toString())) {
				iterator.remove();
			}
		}

		// 客户名称及代理商Id
		Map<String, String[]> clientInfo = new HashMap<>();

		// 查询客户的单价，参数是： 统计日期、客户端ID，短信类型
		Map<String, Object> queryPrice = new HashMap<>();

		// 对数据进行分组 key1为clientid，key2为smstype
		Map<String, Map<Integer, Object[]>> data = new HashMap<>();
		for (AccessChannelStatistics item : statList) {
			Map<Integer, Object[]> clientSmsTypes = data.get(item.getClientid());
			if (clientSmsTypes == null) {
				clientSmsTypes = new HashMap<>();

				// 查询出客户名称，放入clientInfo
				String name = agentInfoMapper.getClientNameByClientId(item.getClientid());
				String agentId = item.getAgentId().toString();
				String[] params = new String[] { name, agentId };
				clientInfo.put(item.getClientid(), params);

				data.put(item.getClientid(), clientSmsTypes);
			}

			// 客户的每一个短信类型，对应值为数组，数组里保存 金额、条数、单价
			Object[] objects = clientSmsTypes.get(item.getSmstype());
			if (objects == null) {
				BigDecimal money = item.getSalefee();
				if (money == null) {
					money = BigDecimal.ZERO;
				}

				// 自己加
				//Integer chargeTotal = item.getChargetotal();

				Integer total = 0;
				total += item.getSubmitsuccess() == null ? 0 : item.getSubmitsuccess();
				total += item.getReportsuccess() == null ? 0 : item.getReportsuccess();
				total += item.getSubmitfail() == null ? 0 : item.getSubmitfail();
				total += item.getReportfail() == null ? 0 : item.getReportfail();

				// 查询出单价，统计报表计算后付费用户费用时，取统计数据对应的发送时间>=生效日期，且生效日期与发送时间最接近的一条记录中的短信单价
				queryPrice.clear(); // 先清空条件
				queryPrice.put("date", date);
				queryPrice.put("clientId", item.getClientid());
				queryPrice.put("smsType", item.getSmstype());
				String unitPrice = agentInfoMapper.getUserPriceByClientId(queryPrice);
				if (StringUtils.isBlank(unitPrice)) {
					unitPrice = "0";
				}

				// objects = new Object[] { money, chargeTotal, unitPrice };
				objects = new Object[] { money, total, unitPrice };
				clientSmsTypes.put(item.getSmstype(), objects);
			} else {
				// 统计表自带的金额累加
				BigDecimal money = (BigDecimal) objects[0];
				money = money.add(item.getSalefee());

				// 统计表计费条数累加
				//Integer chargeTotal = (Integer) objects[1];
				//chargeTotal += item.getChargetotal();

				Integer total = (Integer) objects[1];
				total += item.getSubmitsuccess() == null ? 0 : item.getSubmitsuccess();
				total += item.getReportsuccess() == null ? 0 : item.getReportsuccess();
				total += item.getSubmitfail() == null ? 0 : item.getSubmitfail();
				total += item.getReportfail() == null ? 0 : item.getReportfail();

				// 替换原来的金额以及条数
				objects[0] = money;
				//objects[1] = chargeTotal;
				objects[1] = total;
			}
		}

		// 扣除代理商余额，增加短信账单
		Map<String, Object> params = new HashMap<>(); // 扣费的参数
		AgentBalanceBill agentBalanceBill; // 代理商账单
		StringBuilder remark = new StringBuilder(); // 账单备注
		Map<String,Object> result=new HashedMap();
		// 循环处理所有客户
		for (String clientId : data.keySet()) {
			logger.debug("【每日OEM代理商后付费扣费任务】客户{}开始扣费", clientId);

			// 取出客户的名称及代理商ID
			String[] info = clientInfo.get(clientId);
			String clientName = info[0];
			String agentId = info[1];

			// 取出客户的所有短信类型对应的信息
			Map<Integer, Object[]> smsTypes = data.get(clientId);
			for (Integer smsType : smsTypes.keySet()) {
				// 短信类型对应的 金额、条数、单价
				Object[] objects = smsTypes.get(smsType);

				// BigDecimal statMoney = (BigDecimal) objects[0];
				Integer chargeTotal = (Integer) objects[1];
				String unitPrice = objects[2].toString();
				BigDecimal selfMoney; // 自己算算的金额

				// 查询出代理商余额
				String balance = agentInfoMapper.getAgentBalanceByAgentId(agentId);

				// 计算统计表的价格，统计表是厘所以除以1000，并保留5位小数
				//statMoney = statMoney.divide(new BigDecimal(1000), 5, BigDecimal.ROUND_HALF_UP);

				// 自己计算的价格，条数*单价（单位是元）
				BigDecimal tempMoney = new BigDecimal(chargeTotal).multiply(new BigDecimal(unitPrice));
				String tempMoneyStr = String.valueOf(tempMoney.divide(new BigDecimal(1), 5, BigDecimal.ROUND_HALF_UP).doubleValue());
				selfMoney = new BigDecimal(tempMoneyStr);

				// 目前确定取自己计算的金额
				if (selfMoney.compareTo(BigDecimal.ZERO) <= 0) {
					logger.debug("【每日OEM代理商后付费扣费任务】客户{}开始扣费-短信类型{}, 扣费金额为0跳过扣费", clientId, unitPrice);
					continue;
				}
				BigDecimal money = selfMoney;

				logger.debug("【每日OEM代理商后付费扣费任务】客户{}开始计费: 代理商{} 代理商当前余额{}  短信类型{} 短信单价{} 扣费条数{} 扣费金额{} 扣费日期{}", clientId,
						agentId, balance, smsType, unitPrice, chargeTotal, money, date);

				// 对代理商进行扣费
				/*params.clear();
				params.put("money", money);
				params.put("agentId", Integer.valueOf(agentId));
				agentInfoMapper.subAgentAccountBalance(params);

				// 增加短信账单
				agentBalanceBill = new AgentBalanceBill();
				agentBalanceBill.setAdminId(0l);
				agentBalanceBill.setAgentId(Integer.valueOf(agentId));
				agentBalanceBill.setAmount(money);

				// 算出减去本次后的余额
				BigDecimal currBalance = new BigDecimal(balance).subtract(money);
				// String currBalanceStr = String
				// .valueOf(currBalance.divide(new BigDecimal(1), 5,
				// BigDecimal.ROUND_HALF_UP).doubleValue());
				agentBalanceBill.setBalance(currBalance.divide(new BigDecimal(1), 5, BigDecimal.ROUND_HALF_UP));
				agentBalanceBill.setCreateTime(Calendar.getInstance().getTime());
				agentBalanceBill.setFinancialType("1");
				agentBalanceBill.setOrderId(null);
				agentBalanceBill.setClientId(clientId);
				agentBalanceBill.setPaymentType(6);*/

				// Mod lpjLiu 20171012 v2.3.0 v5.15.0
				// remark.setLength(0);
				// remark.append(clientId);
				// remark.append(":");
				// remark.append(clientName);
				// remark.append(";");
				// remark.append(SMSType.getDescByValue(smsType));
				// 单价
				// remark.append(FmtUtils.hold4Decimal(new
				// BigDecimal(unitPrice)));
				// remark.append("元/条");

				remark.setLength(0);
				remark.append(clientId);
				remark.append("/");
				remark.append(clientName);
				remark.append("/");
				remark.append(SmsTypeEnum.getDescByValue(smsType));
				remark.append("/");
				// 单价
				remark.append(FmtUtils.hold4Decimal(new BigDecimal(unitPrice)));
				remark.append("元");
				remark.append("/");
				remark.append(chargeTotal);
				remark.append("条");
				remark.append("/扣除");
				remark.append(statTimeDesc);

//				agentBalanceBill.setRemark(remark.toString());
//				agentInfoMapper.addAgentBalanceBill(agentBalanceBill);
				result=jsmsSaleCreditService.agentUpdateByBalanceOP(CreditRemarkType.超频.getValue(), SysConstant.SYS_ID,Integer.valueOf(agentId),clientId,money,remark.toString());
				if(Objects.equals(SysConstant.FAIL,result.get("status"))){
					logger.error("【每日OEM代理商后付费扣费任务】客户代理商扣费失败,原因:{},客户ID:{}",JsonUtils.toJson(result),clientId);
					continue;
				}


			}
			logger.debug("【每日OEM代理商后付费扣费任务】客户{}结束扣费", clientId);
		}

		Calendar end = Calendar.getInstance();
		logger.debug("【每日OEM代理商后付费扣费任务】结束 = {}", DateUtils.formatDateTime(end.getTime()));
		return true;
	}

	/**授信改造
	 *代理商相关变化
	 * @param
	 * @param
	 * @return
	 */
	/*private Map<String,Object> agentUpdateByBalanceOP(Integer agnetId,String bType,String clientId, BigDecimal money,String remark){
		Map<String,Object> data=new HashedMap();

		JsmsAgentAccount agentAcc=agentAccountMapper.getByAgentId(agnetId);
		if(agentAcc==null){
			data.put("status","fail");
			data.put("msg","代理商不存在！");
			return	data;
		}

		JsmsAgentAccount agentAccount=new JsmsAgentAccount();
		agentAccount.setAgentId(agentAcc.getAgentId());
		JsmsAgentBalanceBill bill=new JsmsAgentBalanceBill();
		bill.setAgentId(agentAcc.getAgentId());
		bill.setAdminId(0L);

		BigDecimal needCreditMoney=BigDecimal.ZERO;	//需要额外授信金额
		BigDecimal vailBalance=BigDecimal.ZERO;//可用金额

		if(agentAcc.getBalance().compareTo(money)==-1){
			vailBalance=agentAcc.getBalance().add(agentAcc.getCurrentCredit());

			if(vailBalance.compareTo(money)==-1){
				//重新授信补全消费金额
				needCreditMoney=money.subtract(vailBalance);
				data=this.replenish(needCreditMoney,agentAcc.getAgentId());
				if(Objects.equals(SysConstant.FAIL,data.get("status"))){
					return data;
				}
				//更新后,重新取代理商账号信息
				agentAcc=agentAccountMapper.getByAgentId(agentAcc.getAgentId());
				vailBalance=agentAcc.getBalance().add(agentAcc.getCurrentCredit());
			}

			agentAccount.setCurrentCredit(vailBalance.subtract(money));
			agentAccount.setNoBackPayment(agentAcc.getNoBackPayment().add(money.subtract(agentAcc.getBalance())));


			agentAccount.setBalance(agentAcc.getBalance().subtract(money));



			bill.setCurrentCredit(agentAccount.getCurrentCredit());
			bill.setNoBackPayment(agentAccount.getNoBackPayment());
		}else {
			agentAccount.setBalance(agentAcc.getBalance().subtract(money));
			bill.setNoBackPayment(agentAcc.getNoBackPayment());
			bill.setCurrentCredit(agentAcc.getCurrentCredit());

		}
		bill.setCreditBalance(agentAcc.getCreditBalance());
		bill.setHistoryPayment(agentAcc.getHistoryPayment());
		bill.setPaymentType(PaymentType.后付费客户消耗.getValue());
		bill.setFinancialType(FinancialType.出账.getValue());
		agentAccount.setAccumulatedConsume(agentAcc.getAccumulatedConsume().add(money));
		bill.setAmount(money);
		bill.setBalance(agentAccount.getBalance());
		bill.setRemark(remark);
		bill.setOrderId(null);
		bill.setClientId(clientId);
		bill.setCreateTime(new Date());
		int binsert=agentBalanceBillMapper.insert(bill);
		if(binsert>0){
			int accUpdate=agentAccountMapper.updateSelective(agentAccount);
			if(accUpdate<=0){
				data.put("status", "fail");
				data.put("msg", "更新代理商账号金额失败！");
				return data;
			}
		}else {
			data.put("status", "fail");
			data.put("msg", "生成代理商账单失败！");
			return data;
		}
		data.put("status", "success");
		data.put("msg", "更新代理商余额成功！");
		return data;
	}


	*//**
	 * 补全授信额度
	 * @param needCreditMoney
	 * @param agentId
	 * @return
	 *//*
	private Map<String,Object> replenish(BigDecimal needCreditMoney,Integer agentId){

		Map<String,Object> results=new HashedMap();
		//1.财务授信销售
		results=this.finaceCreditForSale(needCreditMoney,agentId);
		if(Objects.equals(SysConstant.FAIL,results.get("result"))){
			return results;
		}else {
			//2.销售授信代理商相关changed
			results=this.creditForAgent(needCreditMoney,agentId);
			if(Objects.equals(SysConstant.FAIL,results.get("result"))){
				return results;
			}else {
				//3.销售授信代理商,销售相关changed
				results=this.creditForSale(needCreditMoney,agentId);
				if(Objects.equals(SysConstant.FAIL,results.get("result"))){
					return results;
				}

			}

		}
		results.put("status","success");
		results.put("msg","金额补充授信成功！");
		return  results;
	}


	private Map<String,Object> finaceCreditForSale(BigDecimal needCreditMoney,Integer agentId){

		Map<String,Object> results=new HashedMap();
		JsmsAgentInfo agent=jsmsAgentInfoMapper.getByAgentId(agentId);
		if(agent==null){
			results.put("status","fail");
			results.put("msg","代理商不存在！");
			return	results;
		}
		if(agent.getBelongSale()==null){
			results.put("status","fail");
			results.put("msg","代理商无归属销售！");
			return	results;
		}
		JsmsSaleCreditAccount saleAccount=jsmsSaleCreditAccountMapper.getBySaleId(agent.getBelongSale());
		if(saleAccount==null){
			results.put("status","fail");
			results.put("msg","销售账户信息不存在！");
			return	results;
		}else {

			JsmsSaleCreditBill bill=new JsmsSaleCreditBill();



			JsmsSaleCreditAccount saleAcc=new JsmsSaleCreditAccount();
			saleAcc.setSaleId(saleAccount.getSaleId());
			//添加授信
			bill.setBusinessType(BusinessType.财务给销售授信.getValue());
			bill.setFinancialType(FinancialType.入账.getValue());

			saleAcc.setCurrentCredit(saleAccount.getCurrentCredit().add(needCreditMoney));
			saleAcc.setFinancialHistoryCredit(saleAccount.getFinancialHistoryCredit().add(needCreditMoney));

			bill.setSaleId(saleAccount.getSaleId());
			bill.setAmount(needCreditMoney);
			bill.setObjectId(saleAccount.getSaleId());
			bill.setObjectType(ObjectType.销售.getValue());
			bill.setFinancialHistoryCredit(saleAccount.getFinancialHistoryCredit());
			bill.setCurrentCredit(saleAccount.getCurrentCredit());
			bill.setSaleHistoryCredit(saleAccount.getSaleHistoryCredit());
			bill.setAgentHistoryPayment(saleAccount.getAgentHistoryPayment());
			bill.setNoBackPayment(saleAccount.getNoBackPayment());
			bill.setAdminId(0L);
			bill.setCreateTime(new Date());
			bill.setRemark("后付费子账户超额");
			//生成账单信息
			int abi=jsmsSaleCreditBillMapper.insert(bill);
			if(abi>0){
				logger.info("生成销售账单信息成功,账单bill={}", JsonUtils.toJson(bill));
				//更新账户信息

				int aup=jsmsSaleCreditAccountMapper.updateSelective(saleAcc);
				if(aup>0){
					logger.info("更新销售账户信息成功,saleAccount={}", JsonUtils.toJson(saleAcc));
					results.put("status","success");
					results.put("msg","更新销售账户信息成功！");

				}else {
					results.put("status","fail");
					results.put("msg","更新销售账户信息失败!");
					logger.error("更新销售账户信息失败,saleAccount={}", JsonUtils.toJson(saleAcc));
					return	results;
				}


			}else {
				results.put("status","fail");
				results.put("msg","生成销售账单信息入库失败!");
				logger.error("生成销售账单信息入库失败,账单bill={}", JsonUtils.toJson(bill));
				return	results;
			}


		}


		results.put("result","fail");
		results.put("msg","金额补充授信成功！");
		return  results;
	}





	*//**
	 * 授信代理商变化
	 * @param
	 * @param
	 * @return
	 *//*
	private  Map<String,Object> creditForAgent(BigDecimal needCreditMoney,Integer agentId){
		//,String agentId,String amount,String operateType
		Map<String,Object> results=new HashedMap();

		JsmsAgentInfo agent=jsmsAgentInfoMapper.getByAgentId(agentId);
		if(agent==null){
			results.put("status","fail");
			results.put("msg","代理商不存在！");
			return	results;
		}else {
			JsmsAgentAccount agentAccount=agentAccountMapper.getByAgentId(agent.getAgentId());
			if(agentAccount==null){
				results.put("status","fail");
				results.put("msg","代理商账户信息不存在！");
				return	results;
			}else {

				JsmsAgentBalanceBill bill=new JsmsAgentBalanceBill();


				JsmsAgentAccount agentAcc1=new JsmsAgentAccount();
				agentAcc1.setAgentId(agentAccount.getAgentId());

					//添加授信
					bill.setPaymentType(PaymentType.增加授信.getValue());
					bill.setFinancialType(FinancialType.入账.getValue());
					agentAcc1.setCurrentCredit(agentAccount.getCurrentCredit().add(needCreditMoney));
					agentAcc1.setCreditBalance(agentAccount.getCreditBalance().add(needCreditMoney));


				bill.setBalance(agentAccount.getBalance());
				bill.setAgentId(agentAccount.getAgentId());
				bill.setAmount(needCreditMoney);
				bill.setCreateTime(new Date());
				bill.setAdminId(0L);
				bill.setRemark("后付费子账户超额");
				bill.setCreditBalance(agentAccount.getCreditBalance());
				bill.setHistoryPayment(agentAccount.getHistoryPayment());
				bill.setCurrentCredit(agentAccount.getCurrentCredit());
				bill.setNoBackPayment(agentAccount.getNoBackPayment());
				//生成账单信息
				int abi=agentBalanceBillMapper.insert(bill);
				if(abi>0){
					logger.info("生成代理商账单信息成功,账单bill={}", JsonUtils.toJson(bill));
					//更新账户信息

					int aup=agentAccountMapper.updateSelective(agentAcc1);
					if(aup>0){
						logger.info("更新代理商账户信息成功,agentAccount={}", JsonUtils.toJson(agentAcc1));
						results.put("status","success");
						results.put("msg","更新代理商账户信息成功！");

					}else {
						results.put("status","fail");
						results.put("msg","更新代理商账户信息失败!");
						logger.error("生成代理商账单信息入库失败,agentAccount={}", JsonUtils.toJson(agentAcc1));
						return	results;
					}


				}else {
					results.put("status","fail");
					results.put("msg","生成代理商账单信息入库失败!");
					logger.error("生成代理商账单信息入库失败,账单bill={}", JsonUtils.toJson(bill));
					return	results;
				}


			}
		}

		results.put("status","success");
		results.put("msg","代理商账单信息更新入库成功!");

		return results;
	}

	*//**
	 * 授信销售变化
	 * @param
	 * @param
	 * @return
	 *//*
	private  Map<String,Object> creditForSale(BigDecimal needCreditMoney,Integer agentId){
		//,String agentId,String amount,String operateType
		Map<String,Object> results=new HashedMap();

		JsmsAgentInfo agent=jsmsAgentInfoMapper.getByAgentId(agentId);
		if(agent==null){
			results.put("status","fail");
			results.put("msg","代理商不存在！");
			return	results;
		}else {

			JsmsSaleCreditAccount saleAccount=jsmsSaleCreditAccountMapper.getBySaleId(agent.getBelongSale());
			if(saleAccount==null){
				results.put("status","fail");
				results.put("msg","销售账户信息不存在！");
				return	results;
			}else {

				JsmsSaleCreditBill bill=new JsmsSaleCreditBill();



				JsmsSaleCreditAccount saleAcc=new JsmsSaleCreditAccount();
				saleAcc.setSaleId(saleAccount.getSaleId());
				//添加授信
				bill.setBusinessType(BusinessType.销售给客户授信.getValue());
				bill.setFinancialType(FinancialType.出账.getValue());
				saleAcc.setSaleHistoryCredit(saleAccount.getSaleHistoryCredit().add(needCreditMoney));
				saleAcc.setCurrentCredit(saleAccount.getCurrentCredit().subtract(needCreditMoney));
				saleAcc.setNoBackPayment(saleAccount.getNoBackPayment().add(needCreditMoney));

				bill.setSaleId(saleAccount.getSaleId());
				bill.setAmount(needCreditMoney);
				bill.setObjectId(Long.valueOf(agentId));
				bill.setObjectType(ObjectType.代理商.getValue());
				bill.setFinancialHistoryCredit(saleAccount.getFinancialHistoryCredit());
				bill.setCurrentCredit(saleAccount.getCurrentCredit());
				bill.setSaleHistoryCredit(saleAccount.getSaleHistoryCredit());
				bill.setAgentHistoryPayment(saleAccount.getAgentHistoryPayment());
				bill.setNoBackPayment(saleAccount.getNoBackPayment());
				bill.setAdminId(0L);
				bill.setCreateTime(new Date());
				bill.setRemark("后付费子账户超额");

				//生成账单信息
				int abi=jsmsSaleCreditBillMapper.insert(bill);
				if(abi>0){
					logger.info("生成销售账单信息成功,账单bill={}", JsonUtils.toJson(bill));
					//更新账户信息

					int aup=jsmsSaleCreditAccountMapper.updateSelective(saleAcc);
					if(aup>0){
						logger.info("更新销售账户信息成功,agentAccount={}", JsonUtils.toJson(saleAcc));
						results.put("status","success");
						results.put("msg","更新销售账户信息成功！");

					}else {
						results.put("status","fail");
						results.put("msg","更新销售账户信息失败!");
						logger.error("更新销售账户信息失败,agentAccount={}", JsonUtils.toJson(saleAcc));
						return	results;
					}


				}else {
					results.put("status","fail");
					results.put("msg","生成销售账单信息入库失败!");
					logger.error("生成销售账单信息入库失败,账单bill={}", JsonUtils.toJson(bill));
					return	results;
				}


			}

		}
		results.put("status","success");
		results.put("msg","销售账单信息更新入库成功!");

		return results;
	}*/
}
