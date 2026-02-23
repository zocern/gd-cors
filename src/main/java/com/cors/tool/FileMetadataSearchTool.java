package com.cors.tool;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.cors.domain.vo.FileMetadataVo;
import com.cors.service.FileMetadataService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FileMetadataSearchTool {

    private final FileMetadataService fileMetadataService;

    @Tool("搜索文件库中的文档。支持模糊搜索文件名或文件正文内容。当用户询问'有没有...','查找...','搜索关于...的资料'时调用此工具。")
    public PageDTO<FileMetadataVo> searchFiles(
            @P("从用户意图中提取核心搜索词。若用户意图模糊，请提炼出最关键、最具代表性的检索词。")
            String keyword,
            @P("目标页码，从 1 开始。用户请求'更多'时，请将此值递增。")
            Optional<Integer> pageNum
    ) {
        return fileMetadataService.getFileByPage(keyword, pageNum.orElse(1), 10);
    }
}