package com.example.ragapp.repository;

import com.example.ragapp.model.DocumentChunk;
import com.example.ragapp.model.SearchResult;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;

/**
 * Decisión: JDBC nativo (JdbcTemplate) en vez de JPA/Hibernate para esta tabla.
 *
 * Por qué: Hibernate no conoce el tipo "vector" de pgvector ni sus operadores
 * de distancia (<-> euclídea, <=> coseno, <#> producto interno). Para que JPA
 * funcione necesitaríamos escribir un UserType/AttributeConverter custom y de
 * todas formas la búsqueda por similitud requiere SQL nativo (ORDER BY
 * embedding <=> ? LIMIT N), que JPQL no expresa bien. En un proyecto de
 * aprendizaje, usar JDBC directo deja la query de pgvector visible y explícita,
 * que es justo lo que queremos entender. Si más adelante el proyecto crece y
 * necesita relaciones/transacciones complejas sobre otras tablas, JPA seguiría
 * siendo válido para esas, conviviendo con este repositorio JDBC para chunks.
 */
@Repository
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long save(String documentName, String content, float[] embedding) {
        String sql = """
                INSERT INTO document_chunks (document_name, content, embedding)
                VALUES (?, ?, ?)
                RETURNING id
                """;
        return jdbcTemplate.queryForObject(sql, Long.class,
                documentName, content, toPgVector(embedding));
    }

    /**
     * Búsqueda por similitud coseno con pgvector.
     *
     * El operador "<=>" devuelve la distancia coseno entre dos vectores
     * (0 = idénticos en dirección, 2 = opuestos). Ordenar por esa distancia
     * ascendente y tomar los primeros N nos da los chunks más parecidos al
     * texto de búsqueda. pgvector puede usar un índice (HNSW o IVFFlat) sobre
     * esta misma expresión para que la búsqueda escale; sin índice, hace un
     * escaneo secuencial calculando la distancia fila por fila (perfectamente
     * razonable con pocos miles de chunks, como en este proyecto).
     */
    public List<SearchResult> search(float[] queryEmbedding, int limit) {
        String sql = """
                SELECT id, document_name, content, embedding <=> ? AS distance
                FROM document_chunks
                ORDER BY distance ASC
                LIMIT ?
                """;
        PGvector vector = toPgVector(queryEmbedding);
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SearchResult(
                        rs.getLong("id"),
                        rs.getString("document_name"),
                        rs.getString("content"),
                        rs.getDouble("distance")),
                vector, limit);
    }

    private PGvector toPgVector(float[] embedding) {
        return new PGvector(embedding);
    }
}
