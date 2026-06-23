package com.example.ragapp.llm;

/**
 * Entrada de la tool MCP "resumir_chunks": deliberadamente más chica que
 * SearchResult (sin id/distance), para que el schema JSON que tiene que
 * completar el LLM orquestador sea simple y no incluya campos que no le
 * sirven (un chunk a resumir no necesariamente vino de una búsqueda).
 */
public record ChunkInput(String documentName, String content) {
}
