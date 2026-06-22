package com.example.ragapp.controller;

import com.example.ragapp.chunking.ChunkingService;
import com.example.ragapp.embedding.EmbeddingService;
import com.example.ragapp.llm.RagAnswerService;
import com.example.ragapp.model.SearchResult;
import com.example.ragapp.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final EmbeddingService embeddingService;
    private final ChunkingService chunkingService;
    private final DocumentChunkRepository repository;
    private final RagAnswerService ragAnswerService;
    private final boolean ragLlmEnabled;

    public DocumentController(EmbeddingService embeddingService,
                               ChunkingService chunkingService,
                               DocumentChunkRepository repository,
                               RagAnswerService ragAnswerService,
                               @Value("${rag.llm.enabled:false}") boolean ragLlmEnabled) {
        this.embeddingService = embeddingService;
        this.chunkingService = chunkingService;
        this.repository = repository;
        this.ragAnswerService = ragAnswerService;
        this.ragLlmEnabled = ragLlmEnabled;
    }

    public record IngestRequest(String documentName, String content) {
    }

    public record IngestResponse(List<Long> ids) {
    }

    public record ChunkPreviewRequest(String content) {
    }

    public record ChunkPreview(int index, int length, String content) {
    }

    // Si el content entra en un solo chunk (caso común con textos cortos), se guarda
    // una sola fila, igual que antes de agregar chunking — sin caso especial que mantener.
    @PostMapping
    public IngestResponse ingest(@RequestBody IngestRequest request) {
        List<String> chunks = chunkingService.chunk(request.content());
        List<Long> ids = chunks.stream()
                .map(chunkText -> {
                    float[] embedding = embeddingService.embed(chunkText);
                    return repository.save(request.documentName(), chunkText, embedding);
                })
                .toList();
        return new IngestResponse(ids);
    }

    // Solo para revisar cómo quedaría dividido un texto antes de ingerirlo de verdad: no genera
    // embeddings ni guarda nada en la base, es puramente el resultado del ChunkingService.
    @PostMapping("/chunks/preview")
    public List<ChunkPreview> previewChunks(@RequestBody ChunkPreviewRequest request) {
        List<String> chunks = chunkingService.chunk(request.content());
        return IntStream.range(0, chunks.size())
                .mapToObj(i -> new ChunkPreview(i, chunks.get(i).length(), chunks.get(i)))
                .toList();
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
