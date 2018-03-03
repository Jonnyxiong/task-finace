package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.access.SmsAccessSendStat;

import java.util.List;
import java.util.Map;

/**
 * @description 客户发送量表
 * @author 黄文杰
 * @date 2017-07-25
 */
public interface SmsAccessSendStatService {

	int insert(SmsAccessSendStat model);

	int insertBatch(List<SmsAccessSendStat> modelList);

	int delete(Integer id);

	int update(SmsAccessSendStat model);

	int updateSelective(SmsAccessSendStat model);

	SmsAccessSendStat getById(Integer id);

	int count(Map<String, Object> params);

	int deleteByDate(Integer date);

	List<SmsAccessSendStat> queryAll(Map params);

	/**
	 * 删除某月的所有数据包括每日的
	 * 
	 * @param yyyyMM
	 * @return
	 */
	int deleteByDateLike(String yyyyMM);

	/**
	 * 查询月发送量所有数据
	 * 
	 * @param yyyyMM
	 * @return
	 */
	List<SmsAccessSendStat> queryByDateLike(String yyyyMM);
}
