package com.cors.config.mcp;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolProviderConfig {

    private final String mcpServerUrl;

    public McpToolProviderConfig(@Value("${langchain4j.web-search-engine.tavily.mcp-server-url}") String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
    }

//    @Bean("tavilyWebSearchToolProvider")
//    public McpToolProvider tavilyWebSearchToolProvider() {
//
//        McpTransport transport = new StreamableHttpMcpTransport.Builder()
//                .url(mcpServerUrl)
//                .logRequests(true) // 打印请求
//                .logResponses(true) // 打印响应
//                .build();
//
//        McpClient mcpClient = new DefaultMcpClient.Builder()
//                .key("tavily")
//                .transport(transport)
//                .build();
//
//        return McpToolProvider.builder()
//                .mcpClients(mcpClient)
//                .build();
//    }
}