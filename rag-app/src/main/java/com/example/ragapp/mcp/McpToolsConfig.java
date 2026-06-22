package com.example.ragapp.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra los métodos @Tool de DocumentSearchTool como tools del servidor
 * MCP (auto-configurado por spring-ai-starter-mcp-server-webmvc).
 */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider documentTools(DocumentSearchTool documentSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(documentSearchTool)
                .build();
    }
}
