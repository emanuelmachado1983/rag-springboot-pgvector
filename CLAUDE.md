# Contexto del proyecto

Sistema RAG (Retrieval Augmented Generation) como proyecto de aprendizaje y portfolio, 100% en stack Java.

Estado actual: **funcionalmente terminado para esta etapa**. Ingestión de documentos, embeddings locales, búsqueda por similitud con pgvector y una capa opcional de generación con LLM ya están implementadas y probadas end-to-end. Ver `README.md` para arquitectura, instrucciones de uso y decisiones de diseño en detalle — este archivo se enfoca en contexto de desarrollo y próximos pasos.

## Sobre mí

Desarrollador backend senior, +18 años de experiencia, principalmente Java/Spring Boot, con fuerte background en sistemas bancarios y PostgreSQL. Explicame brevemente en comentarios del código las partes clave, especialmente todo lo relacionado a embeddings y a las queries con pgvector.

## Stack y decisiones ya tomadas

- Java 21, Maven (con wrapper `mvnw`), Spring Boot 3.5.x.
- 100% stack Java, sin Python.
- Embeddings generados localmente con LangChain4j (`langchain4j-embeddings-all-minilm-l6-v2`, modelo `all-MiniLM-L6-v2`, 384 dimensiones, ONNX embebido en el jar) — sin depender de ninguna API de pago para esta parte. El modelo está mayormente entrenado en inglés: la búsqueda funciona en español, pero el usuario confirmó (probando a mano) que la precisión semántica es notablemente mejor con documentos/preguntas en inglés.
- Persistencia con **JDBC nativo (`JdbcTemplate`), no JPA**, para `document_chunks`: Hibernate no conoce el tipo `vector` de pgvector ni sus operadores de distancia, y la búsqueda por similitud necesita SQL nativo de todas formas. Detalle de la decisión en `DocumentChunkRepository`.
- Binding de `float[]` al tipo `vector` de Postgres vía `com.pgvector:pgvector` (helper oficial, clase `PGvector`).
- **Capa de LLM (Claude vía `langchain4j-anthropic`) ya implementada pero desactivada por default** (`rag.llm.enabled=false`). Se activa con esa property + variable de entorno `ANTHROPIC_API_KEY`. Si se prende sin la key, la app falla al arrancar (fail-fast) en vez de fallar en el primer request. Si la llamada al LLM falla en runtime, el endpoint cae de nuevo a devolver los chunks crudos (fallback), no rompe.
- **Servidor MCP** (`spring-ai-starter-mcp-server-webmvc`, versión 1.1.8 de Spring AI) corriendo en el mismo proceso y puerto que el REST, transporte HTTP/SSE (`GET /sse` + `POST /mcp/message?sessionId=...`). Expone una sola tool, `buscar_documentos_relevantes`, que reutiliza directo `EmbeddingService` y `DocumentChunkRepository` (sin lógica duplicada). Se eligió SSE en vez de stdio para no tener que correr un segundo proceso separado del Spring Boot ya existente. Importante: Spring AI 2.0.0 usa Jackson 3 y rompe contra este stack (Spring Boot 3.5.x / Jackson 2) — por eso se fijó la versión en 1.1.8, no subir sin chequear esa incompatibilidad.
- **Chunking inteligente antes de generar el embedding**: `ChunkingService` usa `DocumentSplitters.recursive(maxChars, overlapChars)` de LangChain4j (módulo `dev.langchain4j:langchain4j`, separado de `langchain4j-core`) — corta primero por párrafo, después por oración, después por palabra, sin partir una idea a la mitad salvo que no quede otra opción. Tamaño y overlap configurables (`rag.chunking.max-chars=400`, `rag.chunking.overlap-chars=50`). Se eligió la librería ya usada en el proyecto en vez de reglas custom de fin-de-oración en español. **Cambio de contrato**: `POST /api/documents` ahora devuelve `{"ids": [...]}` (lista) en vez de `{"id": ...}` — un texto corto sigue generando una sola fila/id, pero dentro de una lista de un elemento.

## Infraestructura

- Postgres con extensión pgvector corriendo en contenedor Docker (`pg-rag`, imagen `pgvector/pgvector:pg16`).
- Conexión: host localhost, puerto 5434, db `ragdb`, user `postgres`, password `rag123`.
- La extensión y la tabla ya están creadas manualmente (`CREATE EXTENSION vector;` + DDL de `document_chunks`, ver README).
- La app corre en `server.port=8082` (definido en `application.properties`; se cambió de 8080/8081 porque esos puertos ya estaban ocupados por otros procesos en la máquina de desarrollo).

## Esquema de la tabla `document_chunks`

- id (serial, PK)
- document_name (varchar)
- content (text)
- embedding (vector, 384 dimensiones)
- created_at (timestamp, default now())

## Estructura del código (`rag-app/src/main/java/com/example/ragapp`)

- `embedding/EmbeddingService.java` — genera el embedding local de un texto.
- `chunking/ChunkingService.java` — divide un texto largo en chunks (párrafo → oración → palabra, con overlap) antes de generar el embedding de cada uno.
- `model/DocumentChunk.java`, `model/SearchResult.java` — records simples, no entidades JPA.
- `repository/DocumentChunkRepository.java` — `JdbcTemplate` + pgvector, insert y búsqueda por distancia coseno (`<=>`).
- `controller/DocumentController.java` — `POST /api/documents` (ingestión, ahora multi-chunk), `POST /api/documents/chunks/preview` (ver la división sin guardar nada) y `GET /api/documents/search` (búsqueda, con o sin LLM según `rag.llm.enabled`).
- `llm/AnthropicConfig.java` — bean condicional del `ChatModel` de Claude, solo se crea si `rag.llm.enabled=true`; valida la API key al arrancar.
- `llm/RagAnswerService.java` — arma el prompt con los chunks como contexto, llama al LLM, maneja el fallback si falla.
- `llm/RagAnswerResponse.java` — DTO de la respuesta cuando la capa de LLM está activa.
- `mcp/DocumentSearchTool.java` — tool MCP `buscar_documentos_relevantes`, llama a los mismos servicios que usa el controller REST.
- `mcp/McpToolsConfig.java` — registra esa tool como `ToolCallbackProvider` para que el auto-config de Spring AI la exponga por MCP.

## Convenciones del proyecto

- Mantener el proyecto simple, sin sobre-ingeniería — es un proyecto de aprendizaje, no un sistema productivo todavía.
- Si hay más de una forma razonable de resolver algo, explicame brevemente las opciones y la razón de la elección, en vez de asumir en silencio.

## Cómo conectar el servidor MCP

Con la app corriendo (puerto 8082) y Docker/Postgres levantados:

```
claude mcp add --transport sse rag-docs http://localhost:8082/sse
claude mcp list   # confirmar que rag-docs aparece conectado
```

Después, en cualquier sesión de Claude Code, preguntar algo que dependa de los documentos cargados (ej. "buscá en mis documentos algo sobre mascotas felinas") — Claude decide solo invocar la tool `buscar_documentos_relevantes`. Si la app deja de correr en ese puerto, `claude mcp list` la va a mostrar desconectada sin romper nada más; no hace falta sacarla salvo que cambie la URL/puerto.

## Próximos pasos posibles

- **Activar y probar la capa de LLM en serio**: cargar crédito en la cuenta de Anthropic, prender `rag.llm.enabled=true` y validar el camino feliz (no solo el fail-fast, que ya está probado).
- **Indexar `embedding` con HNSW o IVFFlat**: hoy la búsqueda hace un escaneo secuencial calculando distancia fila por fila, razonable con pocos chunks; un índice de pgvector sería el siguiente paso si la tabla crece.
- **Ajustar el overlap del chunking**: en las pruebas, el overlap configurado (50 caracteres) no siempre generó texto duplicado visible entre chunks consecutivos cuando el corte caía justo en un límite de oración limpio — revisar si vale la pena forzarlo o si el comportamiento actual de LangChain4j ya es suficiente.
