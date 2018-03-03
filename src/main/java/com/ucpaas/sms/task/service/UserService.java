package com.ucpaas.sms.task.service;

import com.ucpaas.sms.task.entity.message.User;

import java.util.List;
import java.util.Map;

/**
 * @description 用户管理
 * @author 黄文杰
 * @date 2017-07-27
 */
public interface UserService {

	int insert(User model);

	int insertBatch(List<User> modelList);

	int update(User model);

	int updateSelective(User model);

	User getById(Long id);

	int count(Map<String, Object> params);

}
