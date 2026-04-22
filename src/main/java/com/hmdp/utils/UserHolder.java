package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

/**
 * 用户信息持有类
 * 基于ThreadLocal实现用户信息的线程隔离存储，用于在同一个请求的不同层级间传递用户信息
 */
public class UserHolder {
    /**
     * ThreadLocal用于存储当前线程的用户信息
     */
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    /**
     * 保存当前用户信息到ThreadLocal
     *
     * @param user 用户信息DTO
     */
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    /**
     * 从ThreadLocal获取当前用户信息
     *
     * @return 用户信息DTO
     */
    public static UserDTO getUser(){
        return tl.get();
    }

    /**
     * 从ThreadLocal中移除当前用户信息（防止内存泄漏）
     * 建议在请求处理完成后调用此方法
     */
    public static void removeUser(){
        tl.remove();
    }
}