package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 用户服务实现类
 * 提供用户登录、验证码发送等功能，基于Redis实现会话管理
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送手机验证码
     *
     * @param phone 手机号码
     * @param session HTTP会话（当前未使用，验证码存储在Redis中）
     * @return 操作结果
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        
        // 2. 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        
        // 3. 将验证码保存到Redis，设置有效期为2分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        
        // 4. 记录日志（实际项目中这里应该调用短信服务商API发送验证码）
        log.debug("发送短信验证码成功，验证码：{}", code);
        
        return Result.ok();
    }

    /**
     * 用户登录功能（基于验证码登录）
     * 登录流程：校验手机号 -> 校验验证码 -> 查询/创建用户 -> 生成Token -> 保存用户信息到Redis
     *
     * @param loginForm 登录表单数据（包含手机号和验证码）
     * @param session HTTP会话（当前未使用）
     * @return 登录结果，成功时返回Token
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 校验手机号格式
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        
        // 2. 从Redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 验证码不存在或不一致
            return Result.fail("验证码错误");
        }
        
        // 3. 验证码正确，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        
        // 4. 判断用户是否存在，不存在则创建新用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 5. 生成随机Token作为登录凭证
        String token = UUID.randomUUID().toString();
        
        // 6. 将User对象转换为UserDTO，再转为Map存储到Redis Hash结构中
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        
        // 7. 将用户信息存储到Redis Hash结构中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        
        // 8. 设置Token的有效期（36000分钟 = 25天）
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        
        // 9. 返回Token给前端
        return Result.ok(token);
    }

    /**
     * 根据手机号创建新用户
     *
     * @param phone 手机号码
     * @return 创建的用户对象
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        // 生成随机昵称：user_ + 10位随机字符串
        user.setNickName("user_" + RandomUtil.randomString(10));
        save(user);
        return user;
    }


}
