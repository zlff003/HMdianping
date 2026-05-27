package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.BlogComments;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogCommentsMapper;
import com.hmdp.service.IBlogCommentsService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Override
    @Transactional
    public Result addComment(Long blogId, Long parentId, Long answerId, String content) {
        // 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        // 参数校验
        if (blogId == null || content == null || content.trim().isEmpty()) {
            return Result.fail("参数不能为空");
        }

        // 创建评论对象
        BlogComments comment = new BlogComments();
        comment.setUserId(user.getId());
        comment.setBlogId(blogId);
        comment.setParentId(parentId != null ? parentId : 0L);
        comment.setAnswerId(answerId != null ? answerId : 0L);
        comment.setContent(content.trim());
        comment.setLiked(0);
        comment.setStatus(false); // 正常状态（0表示正常）
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        // 保存到数据库
        boolean success = save(comment);
        if (!success) {
            return Result.fail("评论失败");
        }

        return Result.ok(comment);
    }

    @Override
    @Transactional
    public Result deleteComment(Long id) {
        // 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        // 查询评论
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        // 权限校验：只能删除自己的评论
        if (!comment.getUserId().equals(user.getId())) {
            return Result.fail("无权删除该评论");
        }

        // 删除评论（逻辑删除，修改状态）
        comment.setStatus(true); // 设置为被举报状态（1表示被举报）
        comment.setUpdateTime(LocalDateTime.now());
        boolean success = updateById(comment);

        if (!success) {
            return Result.fail("删除失败");
        }

        return Result.ok();
    }

    @Override
    public Result queryCommentsByBlogId(Long blogId, Integer current) {
        // 参数校验
        if (blogId == null) {
            return Result.fail("博客ID不能为空");
        }

        // 分页查询评论列表
        Page<BlogComments> page = query()
                .eq("blog_id", blogId)
                .eq("status", false) // 只查询正常状态的评论（0表示正常）
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<BlogComments> records = page.getRecords();
        
        if (records == null || records.isEmpty()) {
            // 返回空列表时也要返回total
            return Result.ok(Collections.emptyList(), page.getTotal());
        }

        // 查询评论用户信息
        List<Long> userIds = records.stream()
                .map(BlogComments::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<User> users = userService.listByIds(userIds);
        
        // 将用户信息转换为DTO并建立映射
        Map<Long, UserDTO> userMap = users.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> BeanUtil.copyProperties(user, UserDTO.class)
                ));

        // 为每个评论添加用户信息（使用动态属性）
        List<Map<String, Object>> commentResults = records.stream().map(comment -> {
            UserDTO commentUser = userMap.get(comment.getUserId());
            // 创建包含评论和用户信息的返回对象
            Map<String, Object> result = new HashMap<>();
            result.put("id", comment.getId());
            result.put("userId", comment.getUserId());
            result.put("blogId", comment.getBlogId());
            result.put("parentId", comment.getParentId());
            result.put("answerId", comment.getAnswerId());
            result.put("content", comment.getContent());
            result.put("liked", comment.getLiked());
            result.put("createTime", comment.getCreateTime());
            result.put("user", commentUser);
            return result;
        }).collect(Collectors.toList());

        return Result.ok(commentResults, page.getTotal());
    }

    @Override
    @Transactional
    public Result likeComment(Long id) {
        // 获取当前登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }

        // 查询评论
        BlogComments comment = getById(id);
        if (comment == null) {
            return Result.fail("评论不存在");
        }

        // 点赞数加1
        comment.setLiked(comment.getLiked() + 1);
        comment.setUpdateTime(LocalDateTime.now());
        
        boolean success = updateById(comment);
        if (!success) {
            return Result.fail("点赞失败");
        }

        return Result.ok();
    }
}
