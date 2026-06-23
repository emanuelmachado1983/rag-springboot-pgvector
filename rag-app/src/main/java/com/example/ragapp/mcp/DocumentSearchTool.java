package com.example.ragapp.mcp;

import com.example.ragapp.embedding.EmbeddingService;
import com.example.ragapp.model.SearchResult;
import com.example.ragapp.repository.DocumentChunkRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tool MCP: el mismo flujo embedding -> pgvector que ya usa
 * GET /api/documents/search, expuesto para que un LLM (cliente MCP) lo
 * invoque por su cuenta en vez de un humano pegándole al endpoint REST.
 * No hay lógica nueva acá, solo se reutilizan los beans existentes.
 *
 * Se probó forzar la llamada a resumir_chunks acá mismo cuando hay más de 3
 * resultados (en vez de dejarlo a criterio del LLM orquestador), pero se
 * volvió atrás: la decisión de cuándo resumir se deja en manos del LLM,
 * vía el texto de la tool — ver resumir_chunks para el criterio sugerido.
 */
@Service
public class DocumentSearchTool {

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository repository;

    public DocumentSearchTool(EmbeddingService embeddingService, DocumentChunkRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    @Tool(name = "buscar_documentos_relevantes",
            description = "Busca, por similitud semántica, los fragmentos de documentos ya cargados en este "
                    + "sistema que sean más relevantes para una pregunta o tema dado. Usala cuando la pregunta "
                    + "del usuario podría estar respondida en esos documentos. Si el resultado tiene más de 3 "
                    + "fragmentos, considerá pasarlos por la tool resumir_chunks en vez de devolverlos crudos.")
    public List<SearchResult> searchRelevantDocuments(
            @ToolParam(description = "Pregunta o tema a buscar en los documentos") String query,
            @ToolParam(description = "Cantidad máxima de fragmentos a devolver (default 5)", required = false) Integer limit) {
        float[] queryEmbedding = embeddingService.embed(query);
        return repository.search(queryEmbedding, limit != null ? limit : 5);
    }
}
