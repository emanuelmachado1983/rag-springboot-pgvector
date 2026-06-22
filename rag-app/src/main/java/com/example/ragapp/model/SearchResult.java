package com.example.ragapp.model;

/**
 * Un chunk devuelto por la búsqueda de similitud, junto con qué tan "cerca"
 * quedó del texto de búsqueda (0 = idéntico, más alto = menos parecido).
 */
public record SearchResult(
        Long id,
        String documentName,
        String content,
        double distance
) {
}
