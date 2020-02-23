package com.daxiang.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * Created by jiangyitao.
 */
@Data
public class Response<T> {

    private static final Integer SUCCESS = 1;
    private static final Integer FAIL = 0;
    private static final Integer ERROR = -1;

    private static final Integer UNAUTHORIZED = 401;
    private static final Integer ACCESSDENIED = 403;

    private Integer status;
    private String msg;
    private T data;

    private static <T> Response<T> buildResponse(Integer status, String msg, T data) {
        Response response = new Response();
        response.setStatus(status);
        response.setMsg(msg);
        response.setData(data);
        return response;
    }

    @JsonIgnore
    @JSONField(serialize = false)
    public boolean isSuccess() {
        return status == SUCCESS;
    }

    public static Response success() {
        return buildResponse(SUCCESS, "success", null);
    }

    public static <T> Response<T> success(T data) {
        return buildResponse(SUCCESS, "success", data);
    }

    public static Response success(String msg) {
        return buildResponse(SUCCESS, msg, null);
    }

    public static <T> Response<T> success(String msg, T data) {
        return buildResponse(SUCCESS, msg, data);
    }

    public static Response fail(String msg) {
        return buildResponse(FAIL, msg, null);
    }

    public static Response error(String msg) {
        return buildResponse(ERROR, msg, null);
    }

    public static Response unauthorized() {
        return buildResponse(UNAUTHORIZED, "认证失败", null);
    }

    public static Response accessDenied() {
        return buildResponse(ACCESSDENIED, "权限不足", null);
    }

}
