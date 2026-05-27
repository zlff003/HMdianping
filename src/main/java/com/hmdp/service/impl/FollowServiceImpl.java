package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;


    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断关注还是取关
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                //把关注用户的id，放入redis的set集合 sadd userid followUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            //把关注用户的id从redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        
        //查询当前用户关注的所有用户id
        List<Long> currentFollowIds = query()
                .eq("user_id", userId)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        
        if (currentFollowIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        //查询目标用户关注的所有用户id
        List<Long> targetFollowIds = query()
                .eq("user_id", id)
                .list()
                .stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());
        
        if (targetFollowIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        //求交集：找出共同关注的用户id
        currentFollowIds.retainAll(targetFollowIds);
        
        if (currentFollowIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        
        //批量查询共同关注的用户信息
        List<UserDTO> users = userService.listByIds(currentFollowIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
