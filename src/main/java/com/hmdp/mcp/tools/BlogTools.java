package com.hmdp.mcp.tools;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 博客/探店笔记相关工具（标准 MCP 工具）。
 * 工具注册由 McpServerConfiguration 通过 MCP SDK 完成。
 */
@Slf4j
@Component
public class BlogTools {

    @Autowired
    private IBlogService blogService;

    public String getHotBlogs(Long shopId, Integer limit) {

        log.info("[MCP] get_hot_blogs: shopId={}, limit={}", shopId, limit);
        try {
            int size = (limit != null && limit > 0) ? limit : 5;
            Result result = blogService.queryHotBlogList(shopId, null, size);
            if (result == null || result.getData() == null) return "暂无热门笔记";
            Object data = result.getData();
            if (data instanceof List) {
                List<?> blogs = (List<?>) data;
                if (blogs.isEmpty()) return "暂无热门笔记";
                StringBuilder sb = new StringBuilder("热门探店笔记：\n");
                for (Object item : blogs) {
                    if (item instanceof Blog) {
                        Blog b = (Blog) item;
                        String title = b.getTitle() != null ? b.getTitle() : "无标题";
                        sb.append("- ").append(title)
                                .append(" | 点赞: ").append(b.getLiked() != null ? b.getLiked() : 0)
                                .append("\n");
                    }
                }
                return sb.toString().trim();
            }
            return "暂无热门笔记";
        } catch (Exception e) {
            log.error("[MCP] get_hot_blogs 失败", e);
            return "查询热门笔记失败：" + e.getMessage();
        }
    }
}
