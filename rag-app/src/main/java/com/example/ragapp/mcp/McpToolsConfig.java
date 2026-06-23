package com.example.ragapp.mcp;

import com.example.ragapp.llm.SummarizeChunksTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra los métodos @Tool de DocumentSearchTool y SummarizeChunksTool
 * como tools del servidor MCP (auto-configurado por
 * spring-ai-starter-mcp-server-webmvc). Único lugar del proyecto que decide
 * qué tools se exponen.
 */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider documentTools(DocumentSearchTool documentSearchTool,
                                               SummarizeChunksTool summarizeChunksTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(documentSearchTool, summarizeChunksTool)
                .build();
    }
}
