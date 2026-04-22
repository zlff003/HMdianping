package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一返回结果封装类
 * 用于封装API接口的返回数据，包含成功状态、错误信息、数据和总数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    /**
     * 请求是否成功
     */
    private Boolean success;
    
    /**
     * 错误信息（请求失败时返回）
     */
    private String errorMsg;
    
    /**
     * 返回数据
     */
    private Object data;
    
    /**
     * 数据总数（分页查询时使用）
     */
    private Long total;

    /**
     * 构造成功的返回结果（无数据）
     *
     * @return 成功结果
     */
    public static Result ok(){
        return new Result(true, null, null, null);
    }
    
    /**
     * 构造成功的返回结果（带数据）
     *
     * @param data 返回的数据
     * @return 成功结果
     */
    public static Result ok(Object data){
        return new Result(true, null, data, null);
    }
    
    /**
     * 构造成功的返回结果（带数据和总数，用于分页）
     *
     * @param data 返回的数据列表
     * @param total 数据总数
     * @return 成功结果
     */
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total);
    }
    
    /**
     * 构造失败的返回结果
     *
     * @param errorMsg 错误信息
     * @return 失败结果
     */
    public static Result fail(String errorMsg){
        return new Result(false, errorMsg, null, null);
    }
}
