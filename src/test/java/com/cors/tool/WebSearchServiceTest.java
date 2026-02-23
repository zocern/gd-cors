package com.cors.tool;

import com.cors.service.ai.OnlineAssistant;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.DefaultContent;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class WebSearchServiceTest {

    @Autowired
    private OnlineAssistant onlineAssistant;

    @MockitoBean
    private ContentRetriever contentRetriever;

    @Test
    public void testLocalAssistantCallsWebSearchTool() {
        // 1. 准备 Mock 数据
        // ContentRetriever.retrieve 返回的是 List<Content>
        List<Content> mockSearchResults = List.of(
                new DefaultContent("来源：新浪新闻\n内容：今天广州天气晴朗，气温25°C。"),
                new DefaultContent("来源：气象局\n内容：广州今日无雨，微风。")
        );

        // 当 contentRetriever.retrieve(任何查询) 被调用时，返回上面的假数据
        when(contentRetriever.retrieve(any(Query.class)))
                .thenReturn(mockSearchResults);

        // 2. 调用 LocalAssistant
        String session = "1/ONLINE";
        String userMessage = "请帮我搜索一下今天广州的天气";

        // 阻塞式获取结果
        String answer = Objects.requireNonNull(
                onlineAssistant.chat(session, userMessage)
                        .collectList()
                        .block()
        ).stream().reduce("", String::concat);

        System.out.println("AI 返回内容: " + answer);

        // 3. 验证工具调用
        // 验证是否真的调用了底层的 retrieve 方法
        verify(contentRetriever).retrieve(any(Query.class));

        // 4. 验证结果
        // 验证大模型是否读取到了我们 Mock 的内容并生成了回答
        assertThat(answer).contains("晴朗");
        assertThat(answer).contains("25");
    }
}