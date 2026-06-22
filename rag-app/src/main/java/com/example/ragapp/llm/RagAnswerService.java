package com.example.ragapp.llm;

import com.example.ragapp.model.SearchResult;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Capa de "generación" del RAG: toma los chunks ya recuperados por similitud
 * y la pregunta original, le pide al LLM que responda usando ese contexto.
 *
 * chatModel llega como Optional porque el bean (ver AnthropicConfig) solo
 * existe cuando rag.llm.enabled=true; si está vacío, no debería llamarse a
 * este método (el controller decide eso), pero igual lo manejamos por las dudas.
 */
@Service
public class RagAnswerService {

    private static final Logger log = LoggerFactory.getLogger(RagAnswerService.class);

    private final Optional<ChatModel> chatModel;

    public RagAnswerService(Optional<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    public RagAnswerResponse answer(String query, List<SearchResult> chunks) {
        if (chatModel.isEmpty()) {
            return RagAnswerResponse.fallback(chunks, "rag.llm.enabled=true pero no hay ChatModel configurado");
        }

        String context = chunks.stream()
                .map(SearchResult::content)
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                Contexto:
                %s

                Pregunta del usuario: %s

                Respondé la pregunta usando solo la información del contexto. Si el contexto no tiene información suficiente para responder, decilo explícitamente.
                """.formatted(context, query);

        try {
            String answer = chatModel.get().chat(prompt);
            return RagAnswerResponse.generated(answer, chunks);
        } catch (Exception e) {
            log.error("Fallo la llamada a Claude, devolviendo chunks crudos como fallback", e);
            return RagAnswerResponse.fallback(chunks, e.getMessage());
        }
    }
}
