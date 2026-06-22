package com.example.ragapp.llm;

import com.example.ragapp.model.SearchResult;

import java.util.List;

/**
 * Respuesta del endpoint de búsqueda cuando rag.llm.enabled=true.
 * "sources" son siempre los chunks recuperados (para que se pueda verificar
 * de dónde salió la respuesta); "answer" es null si hubo fallback.
 */
public record RagAnswerResponse(
        String answer,
        List<SearchResult> sources,
        boolean fallback,
        String error
) {

    public static RagAnswerResponse generated(String answer, List<SearchResult> sources) {
        return new RagAnswerResponse(answer, sources, false, null);
    }

    public static RagAnswerResponse fallback(List<SearchResult> sources, String error) {
        return new RagAnswerResponse(null, sources, true, error);
    }
}
