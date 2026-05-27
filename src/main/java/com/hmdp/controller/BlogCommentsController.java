package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IBlogCommentsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 */
@RestController
@RequestMapping("/blog-comments")
public class BlogCommentsController {

    @Resource
    private IBlogCommentsService blogCommentsService;

    /**
     * 添加评论
     *
     * @param blogId 博客ID
     * @param parentId 父评论ID，一级评论传0
     * @param answerId 回复的评论ID
     * @param content 评论内容
     * @return 返回结果
     */
    @PostMapping
    public Result addComment(
            @RequestParam("blogId") Long blogId,
            @RequestParam(value = "parentId", defaultValue = "0") Long parentId,
            @RequestParam(value = "answerId", required = false) Long answerId,
            @RequestParam("content") String content) {
        return blogCommentsService.addComment(blogId, parentId, answerId, content);
    }

    /**
     * 删除评论
     *
     * @param id 评论ID
     * @return 返回结果
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return blogCommentsService.deleteComment(id);
    }

    /**
     * 查询博客的评论列表（分页）
     *
     * @param blogId 博客ID
     * @param current 当前页码
     * @return 返回结果
     */
    @GetMapping("/of/blog")
    public Result queryCommentsByBlogId(
            @RequestParam("blogId") Long blogId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogCommentsService.queryCommentsByBlogId(blogId, current);
    }

    /**
     * 点赞评论
     *
     * @param id 评论ID
     * @return 返回结果
     */
    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Long id) {
        return blogCommentsService.likeComment(id);
    }
}
