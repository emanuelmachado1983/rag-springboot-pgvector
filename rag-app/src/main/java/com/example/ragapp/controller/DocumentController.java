package com.example.ragapp.controller;

import com.example.ragapp.embedding.EmbeddingService;
import com.example.ragapp.llm.RagAnswerService;
import com.example.ragapp.model.SearchResult;
import com.example.ragapp.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository repository;
    private final RagAnswerService ragAnswerService;
    private final boolean ragLlmEnabled;

    public DocumentController(EmbeddingService embeddingService,
                               DocumentChunkRepository repository,
                               RagAnswerService ragAnswerService,
                               @Value("${rag.llm.enabled:false}") boolean ragLlmEnabled) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.ragAnswerService = ragAnswerService;
        this.ragLlmEnabled = ragLlmEnabled;
    }

    public record IngestRequest(String documentName, String content) {
    }

    public record IngestResponse(Long id) {
    }

    @PostMapping
    public IngestResponse ingest(@RequestBody IngestRequest request) {
        float[] embedding = embeddingService.embed(request.content());
        Long id = repository.save(request.documentName(), request.content(), embedding);
        return new IngestResponse(id);
    }

    // Con rag.llm.enabled=false devuelve List<SearchResult> tal cual antes (mismo contrato JSON).
    // Con rag.llm.enabled=true devuelve un RagAnswerResponse (answer + sources + fallback/error).
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query,
                                     @RequestParam(defaultValue = "5") int limit) {
        float[] queryEmbedding = embeddingService.embed(query);
        List<SearchResult> chunks = repository.search(queryEmbedding, limit);

        if (!ragLlmEnabled) {
            return ResponseEntity.ok(chunks);
        }
        return ResponseEntity.ok(ragAnswerService.answer(query, chunks));
    }
}
