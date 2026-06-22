# Contexto del proyecto

Sistema RAG (Retrieval Augmented Generation) como proyecto de aprendizaje y portfolio.

## Sobre mí

Desarrollador backend senior, +18 años de experiencia, principalmente Java/Spring Boot, con fuerte background en sistemas bancarios y PostgreSQL. Explicame brevemente en comentarios del código las partes clave, especialmente todo lo relacionado a embeddings y a las queries con pgvector.

## Stack y decisiones ya tomadas

- Java 17+, Maven, Spring Boot (última versión estable).
- Quiero quedarme 100% en el stack Java, sin Python.
- Embeddings generados localmente con LangChain4j (modelo `all-MiniLM-L6-v2`, 384 dimensiones), sin depender de ninguna API de pago en esta etapa.
- Todavía NO se integra ningún LLM (ni Claude ni OpenAI) — eso se agrega en un paso posterior. El objetivo actual es: ingestión de documentos + búsqueda por similitud funcionando end-to-end.

## Infraestructura

- Postgres con extensión pgvector corriendo en contenedor Docker.
- Conexión: host localhost, puerto 5434, db `ragdb`, user `postgres`, password `rag123`.
- La extensión ya está creada manualmente (`CREATE EXTENSION vector;`).

## Esquema de la tabla `document_chunks`

- id (serial, PK)
- document_name (varchar)
- content (text)
- embedding (vector, 384 dimensiones)
- created_at (timestamp, default now())

## Convenciones del proyecto

- Mantener el proyecto simple, sin sobre-ingeniería — es un proyecto de aprendizaje, no un sistema productivo todavía.
- Si hay más de una forma razonable de resolver algo (por ejemplo, JPA vs JDBC nativo para el campo vector), explicame brevemente las opciones y la razón de la elección, en vez de asumir en silencio.
