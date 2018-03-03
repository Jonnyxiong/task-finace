package com.ucpaas.sms.task.exception;

/**
 * @description 客户失败返回清单表
 * @author lpjLiu
 * @date 2017-10-11
 */
public class ClientFailReturnException extends  RuntimeException{

    public ClientFailReturnException(String message) {
        super(message);
    }
    
}
