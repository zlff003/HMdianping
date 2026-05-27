package com.hmdp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.BlogComments;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IBlogCommentsService extends IService<BlogComments> {

    /**
     * 添加评论
     *
     * @param blogId 博客ID
     * @param parentId 父评论ID，一级评论传0
     * @param answerId 回复的评论ID
     * @param content 评论内容
     * @return 返回结果
     */
    Result addComment(Long blogId, Long parentId, Long answerId, String content);

    /**
     * 删除评论
     *
     * @param id 评论ID
     * @return 返回结果
     */
    Result deleteComment(Long id);

    /**
     * 查询博客的评论列表（分页）
     *
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 返回结果
     */
    Result queryCommentsByBlogId(Long blogId, Integer current);

    /**
     * 点赞评论
     *
     * @param id 评论ID
     * @return 返回结果
     */
    Result likeComment(Long id);
}
