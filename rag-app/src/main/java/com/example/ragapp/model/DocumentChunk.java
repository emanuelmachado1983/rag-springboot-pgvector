package com.example.ragapp.model;

import java.time.LocalDateTime;

/**
 * Representa una fila de document_chunks. No es una @Entity de JPA a propósito:
 * ver DocumentChunkRepository para la explicación de por qué este proyecto usa JDBC nativo.
 */
public record DocumentChunk(
        Long id,
        String documentName,
        String content,
        float[] embedding,
        LocalDateTime createdAt
) {
}
