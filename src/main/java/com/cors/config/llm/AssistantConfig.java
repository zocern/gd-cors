package com.cors.config.llm;

import com.cors.service.ai.LocalAssistant;
import com.cors.service.ai.OnlineAssistant;
import com.cors.tool.FileMetadataSearchTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



@Configuration
@RequiredArgsConstructor
public class AssistantConfig {


    @Bean("localAssistant")
    public LocalAssistant localAssistant(
            @Qualifier("qwenChatModel") OllamaStreamingChatModel ollamaStreamingChatModel,
            ChatMemoryProvider chatMemoryProvider,
            RetrievalAugmentor retrievalAugmentor,
            FileMetadataSearchTool fileMetadataSearchTool
    ) {
        return AiServices.builder(LocalAssistant.class)
                .streamingChatModel(ollamaStreamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .retrievalAugmentor(retrievalAugmentor)
                .tools(fileMetadataSearchTool)
                .build();
    }

    @Bean("onlineAssistant")
    public OnlineAssistant onlineAssistant(
            @Qualifier("onlineChatModel") OpenAiStreamingChatModel openAiStreamingChatModel,
            ChatMemoryProvider chatMemoryProvider
            // @Qualifier("tavilyWebSearchToolProvider") McpToolProvider toolProvider
            ) {
        return AiServices.builder(OnlineAssistant.class)
                .streamingChatModel(openAiStreamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                // .toolProvider(toolProvider)
                .build();
    }
}