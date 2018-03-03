package com.ucpaas.sms.task.mapper.message;

import com.ucpaas.sms.task.entity.message.AgentBalanceBill;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public interface AgentInfoMapper {
	List<String> findOemAgentInfoByAgentIds(@Param("set") Set<String> ids);

	List<String> findOemAgentIdList();

	String getClientNameByClientId(String clientId);

	String getAgentBalanceByAgentId(String agentId);

	String getUserPriceByClientId(Map params);

	int checkAccessChannelStatDone(String date);

	int checkOemHouFuIsRunByDate(String date);

	int checkAccessChannelFourdayStatDone(String date);

	int subAgentAccountBalance(Map params);

	int addAgentAccountBalance(Map params);

	int addAgentBalanceBill(AgentBalanceBill agentBalanceBill);
}