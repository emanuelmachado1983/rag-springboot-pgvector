package com.example.ragapp.llm;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Este bean solo se crea si rag.llm.enabled=true (ver @ConditionalOnProperty).
 * Con el toggle apagado, Spring ni intenta instanciar el cliente de Claude:
 * no hace falta tener la API key configurada para seguir usando el modo
 * retrieval-only.
 *
 * Si está prendido pero falta la key, fallamos al arrancar la app (fail-fast)
 * en vez de fallar recién cuando llega el primer request.
 */
@Configuration
public class AnthropicConfig {

    @Bean
    @ConditionalOnProperty(name = "rag.llm.enabled", havingValue = "true")
    public ChatModel anthropicChatModel(
            @Value("${anthropic.api.key}") String apiKey,
            @Value("${anthropic.model}") String model) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "rag.llm.enabled=true pero anthropic.api.key está vacía. "
                            + "Configurá la variable de entorno ANTHROPIC_API_KEY o volvé a poner rag.llm.enabled=false.");
        }

        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(model)
                .build();
    }
}
