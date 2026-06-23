package com.example.ragapp.llm;

import java.util.List;

/**
 * Resultado de la tool MCP "resumir_chunks". Mismo patrón que
 * RagAnswerResponse: "fallback" le permite al LLM orquestador distinguir
 * un resumen real de un mensaje de error, sin tener que parsear prosa.
 */
public record SummaryResult(
        String summary,
        List<String> sourceDocuments,
        boolean fallback,
        String error
) {

    public static SummaryResult generated(String summary, List<String> sourceDocuments) {
        return new SummaryResult(summary, sourceDocuments, false, null);
    }

    public static SummaryResult fallback(List<String> sourceDocuments, String error) {
        return new SummaryResult(null, sourceDocuments, true, error);
    }
}
