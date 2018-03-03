package com.ucpaas.sms.task.mapper.record;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.ucpaas.sms.task.entity.record.RecordChannelTempStatistics;

@Repository
public interface RecordChannelTempDataStatisticsMapper{

	int insert(RecordChannelTempStatistics model);
	
	int insertBatch(List<RecordChannelTempStatistics> modelList);
	
	int delete(Integer channelid);
	
	int update(RecordChannelTempStatistics model);
	
	int updateSelective(RecordChannelTempStatistics model);
	
	RecordChannelTempStatistics getByChannelid(Integer channelid);

	List<RecordChannelTempStatistics> generateData(Map<String,Object> params);
	
	int count(Map<String,Object> params);

	List<RecordChannelTempStatistics> generateDataFromRecord(Map<String,Object> params);

}