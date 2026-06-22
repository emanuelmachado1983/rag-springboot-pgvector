package com.example.ragapp.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Un "embedding" es la representación numérica del significado de un texto:
 * un vector de N números (acá, 384) tal que textos con significado parecido
 * quedan "cerca" en ese espacio de N dimensiones. No hay nada mágico en el
 * número 384: es simplemente la cantidad de dimensiones con la que fue
 * entrenado el modelo all-MiniLM-L6-v2 (un modelo chico de Sentence-Transformers,
 * convertido a formato ONNX para poder correrlo en Java sin Python ni GPU).
 *
 * LangChain4j empaqueta el modelo (los pesos .onnx) directo en el jar de la
 * dependencia langchain4j-embeddings-all-minilm-l6-v2, así que esto corre
 * 100% local: no pega ningún llamado a una API externa.
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(TextSegment.from(text)).content();
        return embedding.vector();
    }
}
