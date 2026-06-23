package com.example.ragapp.llm;

import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tool MCP para function calling encadenado: el LLM orquestador puede
 * llamarla después de "buscar_documentos_relevantes" si decide que conviene
 * sintetizar los resultados en vez de devolverlos crudos. La app no decide
 * esto por su cuenta, solo expone la tool.
 *
 * Se probó forzar esta llamada desde DocumentSearchTool cuando hay más de 3
 * resultados, pero se volvió atrás: la decisión queda en manos del LLM
 * orquestador, guiada por el criterio sugerido en la descripción de la tool.
 *
 * chatModel llega como Optional por el mismo motivo que en RagAnswerService:
 * el bean (ver AnthropicConfig) solo existe si rag.llm.enabled=true.
 */
@Service
public class SummarizeChunksTool {

    private static final Logger log = LoggerFactory.getLogger(SummarizeChunksTool.class);

    private final Optional<ChatModel> chatModel;

    public SummarizeChunksTool(Optional<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Tool(name = "resumir_chunks",
            description = "Combina una lista de fragmentos de documentos (por ejemplo, los devueltos por "
                    + "buscar_documentos_relevantes) en un resumen único y compacto, eliminando redundancia entre "
                    + "ellos. Usala cuando buscar_documentos_relevantes haya devuelto más de 3 fragmentos, en vez "
                    + "de devolverlos crudos. Con 3 fragmentos o menos, no es necesario resumir. Requiere que el "
                    + "LLM esté habilitado en el servidor; si no lo está, devuelve un resultado indicándolo en vez "
                    + "de fallar.")
    public SummaryResult summarizeChunks(
            @ToolParam(description = "Lista de fragmentos a resumir, cada uno con su documento de origen y contenido")
            List<ChunkInput> chunks) {

        if (chunks == null || chunks.isEmpty()) {
            return SummaryResult.fallback(List.of(), "No se recibieron fragmentos para resumir");
        }

        List<String> sourceDocuments = chunks.stream()
                .map(ChunkInput::documentName)
                .distinct()
                .toList();

        if (chatModel.isEmpty()) {
            return SummaryResult.fallback(sourceDocuments,
                    "Esta herramienta requiere que el LLM esté habilitado (rag.llm.enabled=true). "
                            + "Actualmente está deshabilitado, así que no se puede generar un resumen.");
        }

        String context = chunks.stream()
                .map(chunk -> "[%s]\n%s".formatted(chunk.documentName(), chunk.content()))
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                A continuación hay varios fragmentos de documentos, cada uno precedido por el nombre del
                documento de origen entre corchetes.

                %s

                Generá un resumen único y compacto que combine la información de todos los fragmentos.

                Reglas estrictas:
                - Usá exclusivamente la información presente en los fragmentos. No agregues datos, hechos,
                  fechas ni cifras que no estén en el texto.
                - Si dos o más fragmentos repiten la misma información, mencionala una sola vez.
                - Si los fragmentos contienen información contradictoria entre sí, señalá la contradicción
                  en vez de elegir una versión arbitrariamente.
                - No especules ni completes huecos de información con conocimiento general externo a estos
                  fragmentos.
                - Si los fragmentos no tienen relación entre sí o no permiten armar un resumen coherente,
                  decilo explícitamente en vez de forzar una síntesis artificial.
                - Respondé directamente con el resumen, sin frases introductorias como "Este resumen..." o
                  "A continuación se presenta...".
                """.formatted(context);

        try {
            String summary = chatModel.get().chat(prompt);
            return SummaryResult.generated(summary, sourceDocuments);
        } catch (Exception e) {
            log.error("Fallo la llamada a Claude al resumir chunks, devolviendo fallback", e);
            return SummaryResult.fallback(sourceDocuments, e.getMessage());
        }
    }
}
