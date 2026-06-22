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
                    + "del usuario podría estar respondida en esos documentos.")
    public List<SearchResult> buscarDocumentosRelevantes(
            @ToolParam(description = "Pregunta o tema a buscar en los documentos") String query,
            @ToolParam(description = "Cantidad máxima de fragmentos a devolver (default 5)", required = false) Integer limit) {
        float[] queryEmbedding = embeddingService.embed(query);
        return repository.search(queryEmbedding, limit != null ? limit : 5);
    }
}
