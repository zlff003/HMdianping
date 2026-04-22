package com.hmdp.controller;


import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 用户控制器
 * 提供用户相关的HTTP接口，包括登录、登出、信息查询等功能
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     *
     * @param phone 手机号码
     * @param session HTTP会话
     * @return 操作结果
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 用户登录功能
     *
     * @param loginForm 登录表单数据，包含手机号、验证码；或者手机号、密码
     * @param session HTTP会话
     * @return 登录结果，成功时返回Token
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 用户登出功能
     *
     * @return 操作结果
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 实现登出功能（删除Redis中的Token）
        return Result.fail("功能未完成");
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public Result me(){
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    /**
     * 查询指定用户的详细信息
     *
     * @param userId 用户ID
     * @return 用户详细信息
     */
    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 根据用户ID查询详细信息
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，可能是第一次查看详情
            return Result.ok();
        }
        
        // 清除时间字段，不返回给前端
        info.setCreateTime(null);
        info.setUpdateTime(null);
        
        return Result.ok(info);
    }
}
