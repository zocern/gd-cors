//package com.example.tool;
//
//import dev.langchain4j.agent.tool.Tool;
//import dev.langchain4j.rag.content.Content;
//import dev.langchain4j.rag.content.retriever.ContentRetriever;
//import dev.langchain4j.rag.query.Query;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Component
//@RequiredArgsConstructor
//public class WebSearchTool {
//
//    private final ContentRetriever contentRetriever;
//
//    @Tool("当需要查询实时信息或外部知识时调用。")
//    public String searchWeb(String query) {
//        // 执行检索，拿到原始的 Content 列表
//        List<Content> contents = contentRetriever.retrieve(Query.from(query));
//
//        // 直接拼接成字符串
//        if (contents.isEmpty()) {
//            return "未找到相关结果。";
//        }
//
//        return contents.stream()
//                .map(content -> content.textSegment().text())
//                .collect(Collectors.joining("\n\n---\n\n"));
//    }
//}