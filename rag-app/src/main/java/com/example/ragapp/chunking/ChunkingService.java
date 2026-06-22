package com.example.ragapp.chunking;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Divide un texto largo en fragmentos más chicos antes de generar el
 * embedding de cada uno. Usa el splitter "recursivo" de LangChain4j: intenta
 * cortar por párrafo, y si un párrafo no entra en maxChars, baja a oración, y
 * si una oración no entra, a palabra — nunca corta una idea a la mitad salvo
 * que no quede otra opción. El overlap repite el final de un chunk al
 * principio del siguiente, para no perder contexto justo en el borde del
 * corte (relevante para que la búsqueda por similitud no "pierda" una idea
 * que quedó partida entre dos chunks).
 *
 * Si el texto entra en un solo chunk (caso común con textos cortos), el
 * splitter devuelve una lista de un solo elemento — mismo comportamiento de
 * antes, sin casos especiales que mantener.
 */
@Service
public class ChunkingService {

    private final DocumentSplitter splitter;

    public ChunkingService(
            @Value("${rag.chunking.max-chars:400}") int maxChars,
            @Value("${rag.chunking.overlap-chars:50}") int overlapChars) {
        this.splitter = DocumentSplitters.recursive(maxChars, overlapChars);
    }

    public List<String> chunk(String text) {
        return splitter.split(Document.from(text))
                .stream()
                .map(TextSegment::text)
                .toList();
    }
}
