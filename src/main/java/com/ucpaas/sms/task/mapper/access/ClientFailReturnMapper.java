package com.ucpaas.sms.task.mapper.access;

import com.jsmsframework.common.dto.JsmsPage;
import com.ucpaas.sms.task.entity.access.ClientFailReturn;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * @description 客户失败返回清单表
 * @author lpjLiu
 * @date 2017-10-11
 */
@Repository
public interface ClientFailReturnMapper {

	int insert(ClientFailReturn model);

	int insertBatch(List<ClientFailReturn> modelList);

	int update(ClientFailReturn model);

	int updateSelective(ClientFailReturn model);

	int updateBelongSale(ClientFailReturn model);

	int updateBatchBelongSale(List<ClientFailReturn> modelList);

	int updateBatchAgentId(List<ClientFailReturn> modelList);

	int updateBatchDepartmentId(List<ClientFailReturn> modelList);

	int updateBatchOperatorstype(List<ClientFailReturn> modelList);

	ClientFailReturn getById(Integer id);

	List<ClientFailReturn> findHasReturnGroupByDay();

	String getStartFixDay();
	String getEndFixDay();

	List<ClientFailReturn> queryList(JsmsPage<ClientFailReturn> page);

	List<ClientFailReturn> findList(ClientFailReturn model);

	int count(Map<String, Object> params);

	int deleteByDate(Integer date);
}