# RAG App — búsqueda semántica sobre documentos propios

Sistema RAG (Retrieval Augmented Generation) 100% en stack Java, hecho como proyecto de aprendizaje y portfolio. Permite cargar fragmentos de texto ("chunks"), generarles un embedding local, guardarlos en Postgres con `pgvector`, y después buscar los más parecidos semánticamente a una pregunta. Opcionalmente (desactivado por default), puede usar un LLM (Claude) para generar una respuesta en lenguaje natural a partir de esos chunks. También expone esa misma búsqueda como tool de un servidor MCP, para que un LLM (ej. Claude Code) la invoque directamente.

## Arquitectura

```
                 POST /api/documents
                        |
                        v
                ChunkingService  (LangChain4j DocumentSplitters.recursive: párrafo -> oración -> palabra, con overlap)
                        |
                        v  (uno o más chunks)
                EmbeddingService  (LangChain4j, modelo local all-MiniLM-L6-v2, 384 dims)
                        |
                        v
              DocumentChunkRepository  (JDBC -> Postgres + pgvector, una fila por chunk)
                        |
                        v
                  document_chunks  (tabla con columna "vector(384)")


                 POST /api/documents/chunks/preview
                        |
                        v
                ChunkingService  -- devuelve los chunks sin generar embeddings ni guardar nada


                 GET /api/documents/search?query=...
                        |
                        v
                EmbeddingService.embed(query)
                        |
                        v
        DocumentChunkRepository.search()  -- ORDER BY embedding <=> ? (distancia coseno)
                        |
                        v
        rag.llm.enabled=false --------------------> devuelve los chunks crudos
        rag.llm.enabled=true  --> RagAnswerService --> Claude (LangChain4j) --> respuesta generada
                                       |
                                       v (si falla la llamada al LLM)
                                  fallback a chunks crudos


        Cliente MCP (ej. Claude Code) -- GET /sse + POST /mcp/message
                        |
                        v
        DocumentSearchTool.buscarDocumentosRelevantes(query, limit)
                        |
                        v
        EmbeddingService.embed(query) -> DocumentChunkRepository.search()
        (mismos beans que usa el endpoint REST, sin lógica duplicada)
                        |
                        v (a criterio del LLM orquestador, no automático)
        SummarizeChunksTool.resumirChunks(chunks)  --> Claude --> resumen único
```

## Stack

- Java 21, Maven (wrapper incluido, no hace falta tener Maven instalado).
- Spring Boot 3.5.x (`spring-boot-starter-web`, `spring-boot-starter-jdbc`).
- PostgreSQL + extensión [pgvector](https://github.com/pgvector/pgvector), corriendo en Docker.
- [LangChain4j](https://docs.langchain4j.dev/) para:
  - Embeddings locales (`langchain4j-embeddings-all-minilm-l6-v2`, modelo ONNX embebido en el jar, corre en CPU, sin llamadas externas).
  - Cliente de Claude/Anthropic (`langchain4j-anthropic`), usado solo si se activa la capa de LLM.
- [`com.pgvector:pgvector`](https://github.com/pgvector/pgvector-java): helper oficial para bindear `float[]` al tipo `vector` de Postgres desde JDBC.
- [Spring AI](https://docs.spring.io/spring-ai/reference/) (`spring-ai-starter-mcp-server-webmvc`, versión 1.1.8) para exponer un servidor MCP en el mismo proceso/puerto, transporte HTTP/SSE. **Importante**: la versión 2.0.0 de Spring AI usa Jackson 3 y rompe contra este stack (Spring Boot 3.5.x trae Jackson 2) — no actualizar sin chequear esa incompatibilidad primero.
- Chunking inteligente vía el módulo `dev.langchain4j:langchain4j` (no `langchain4j-core`), que trae los `DocumentSplitter` ya implementados (`DocumentSplitters.recursive`).

## Instalación y ejecución desde cero

### 1. Levantar Postgres con pgvector

```bash
docker run --name pg-rag -e POSTGRES_PASSWORD=rag123 -e POSTGRES_DB=ragdb -p 5434:5432 -d pgvector/pgvector:pg16
```

Conectarse y activar la extensión + crear la tabla:

```bash
docker exec -it pg-rag psql -U postgres -d ragdb
```

```sql
CREATE EXTENSION vector;

CREATE TABLE document_chunks (
    id SERIAL PRIMARY KEY,
    document_name VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding VECTOR(384),
    created_at TIMESTAMP DEFAULT now()
);
```

### 2. Compilar y correr la app

```bash
cd rag-app
./mvnw spring-boot:run        # o .\mvnw.cmd spring-boot:run en Windows
```

Por defecto la app escucha en el puerto configurado en `server.port` (`application.properties`). Conexión a la base, usuario y password también se configuran ahí (deben matchear el contenedor de Docker del paso 1).

### Variables de entorno

| Variable             | Obligatoria | Para qué |
|-----------------------|-------------|----------|
| `ANTHROPIC_API_KEY`   | Solo si `rag.llm.enabled=true` | API key de Claude/Anthropic. Si falta y el flag está en `true`, la app falla al arrancar (fail-fast) con un mensaje claro. |

## Uso de los endpoints

### Ingestar un documento

El `content` se divide automáticamente en chunks (por párrafo/oración, con overlap) antes de generar los embeddings — un texto corto sigue generando una sola fila, uno largo puede generar varias.

```bash
curl -X POST http://localhost:8082/api/documents \
  -H "Content-Type: application/json" \
  -d '{"documentName":"animales.txt","content":"El gato es un animal domestico muy popular como mascota"}'
```

```json
{ "ids": [1] }
```

Con un texto más largo (varios párrafos), la respuesta trae un id por cada chunk generado: `{ "ids": [6, 7, 8, 9] }`.

> **Nota de compatibilidad**: antes de agregar chunking, este endpoint devolvía `{ "id": 1 }` (singular). Ahora siempre devuelve `{ "ids": [...] }` (lista), aunque sea de un solo elemento.

### Ver cómo queda dividido un texto antes de ingerirlo

No genera embeddings ni guarda nada — solo corre el `ChunkingService` para poder revisar el resultado:

```bash
curl -X POST http://localhost:8082/api/documents/chunks/preview \
  -H "Content-Type: application/json" \
  -d '{"content":"Un texto largo con varios párrafos..."}'
```

```json
[
  { "index": 0, "length": 245, "content": "El sistema RAG combina dos técnicas..." },
  { "index": 1, "length": 162, "content": "Estos vectores capturan el significado..." }
]
```

### Buscar por similitud

```bash
curl "http://localhost:8082/api/documents/search?query=mascotas%20felinas&limit=3"
```

Con `rag.llm.enabled=false` (default), devuelve los chunks más parecidos tal cual están guardados, ordenados por distancia coseno (más cerca de 0 = más parecido):

```json
[
  { "id": 1, "documentName": "animales.txt", "content": "El gato es un animal domestico...", "distance": 0.468 },
  { "id": 3, "documentName": "animales.txt", "content": "Los perros son leales...", "distance": 0.734 }
]
```

Con `rag.llm.enabled=true`, el mismo endpoint devuelve una respuesta generada por el LLM, junto con las fuentes usadas:

```json
{
  "answer": "Según el contexto, el gato es...",
  "sources": [ /* mismos chunks de arriba */ ],
  "fallback": false,
  "error": null
}
```

Si la llamada a Claude falla (sin crédito, sin conexión, rate limit), `fallback` pasa a `true`, `answer` queda en `null` y `error` trae el detalle — el endpoint no rompe, simplemente degrada al modo retrieval-only.

> **Nota sobre el idioma**: `all-MiniLM-L6-v2` está entrenado mayormente en inglés. La búsqueda funciona en español, pero los resultados son más precisos con documentos y preguntas en inglés. Si la precisión semántica importa más que mantener todo en español, conviene ingerir/preguntar en inglés.

## Servidor MCP

Además del REST, el servidor MCP (corriendo en el mismo proceso y puerto que el resto de la app, vía transporte HTTP/SSE) expone dos tools:

- **`buscar_documentos_relevantes`**: la misma búsqueda por similitud del endpoint REST (`query`, `limit` opcional). Devuelve los chunks crudos, ordenados por distancia coseno.
- **`resumir_chunks`**: recibe una lista de fragmentos (típicamente los que devolvió `buscar_documentos_relevantes`) y los combina en un resumen único, eliminando redundancia. Pensada para "function calling encadenado": el LLM orquestador puede llamarla por su cuenta después de buscar, si decide que conviene sintetizar en vez de mostrar los chunks crudos — el criterio sugerido en su descripción es "más de 3 fragmentos". Esta decisión queda **100% a criterio del LLM**, no hay nada en el servidor que la fuerce (se probó forzarla y se volvió atrás, ver Decisiones de diseño). Requiere `rag.llm.enabled=true`; si está apagado, devuelve un resultado con `fallback=true` en vez de fallar.

Con la app y Postgres corriendo, para conectarlo desde Claude Code:

```bash
claude mcp add --transport sse rag-docs http://localhost:8082/sse
claude mcp list   # confirmar que rag-docs aparece conectado
```

Después, en cualquier sesión de Claude Code, basta con preguntar algo que dependa de los documentos cargados (ej. "buscá en mis documentos algo sobre mascotas felinas") — Claude decide solo cuándo invocar cada tool. Si la app no está corriendo, `claude mcp list` la muestra desconectada sin romper nada más.

Para probarlo manualmente sin un cliente MCP, el protocolo es JSON-RPC sobre SSE: `GET /sse` devuelve un `sessionId`, y los mensajes (`initialize`, `tools/list`, `tools/call`) se mandan por `POST /mcp/message?sessionId=...`, con las respuestas llegando por el stream SSE abierto.

## Cómo activar la capa de LLM

1. Conseguir una API key de Anthropic con crédito cargado.
2. Exportar la variable de entorno: `export ANTHROPIC_API_KEY=sk-ant-...` (o `$env:ANTHROPIC_API_KEY` en PowerShell).
3. Poner `rag.llm.enabled=true` en `application.properties` (o pasarlo como `--rag.llm.enabled=true` al arrancar).
4. El modelo usado por defecto es `claude-haiku-4-5-20251001` (configurable en `anthropic.model`), elegido por ser el más económico de la familia Claude — suficiente para esta etapa del proyecto.

Con el flag en `false` (o sin setearlo), el comportamiento es exactamente el mismo que antes de que existiera esta funcionalidad.

## Decisiones de diseño

- **Embeddings locales en vez de una API paga**: el objetivo de esta etapa es tener ingestión + búsqueda semántica funcionando sin depender de crédito en ninguna cuenta. `all-MiniLM-L6-v2` corre en CPU vía ONNX, embebido en el jar de LangChain4j — cero llamadas externas, cero costo, ideal para aprender y para un portfolio que cualquiera pueda levantar sin configurar nada pago.
- **JDBC en vez de JPA para `document_chunks`**: Hibernate no conoce el tipo `vector` de pgvector ni sus operadores de distancia (`<=>`, `<->`, `<#>`). Habría que escribir un `UserType` custom igual, y la búsqueda por similitud necesita SQL nativo de todas formas. Usar `JdbcTemplate` directo deja la query de pgvector visible y explícita, que es justo lo que se quiere entender en un proyecto de aprendizaje.
- **Capa de LLM opcional y desactivable por property**: se construyó para poder probar el flujo de generación más adelante sin comprometerse a tener crédito en la cuenta de Anthropic desde ya. El bean de Claude (`AnthropicChatModel`) sólo se crea si `rag.llm.enabled=true` (vía `@ConditionalOnProperty`), y si está prendido pero falta la API key, la app falla al arrancar en vez de fallar silenciosamente en el primer request. Si la llamada al LLM falla en runtime, el endpoint cae de nuevo al modo retrieval-only en vez de romper.
- **MCP por HTTP/SSE en el mismo proceso, en vez de stdio**: con stdio, un cliente como Claude Desktop lanzaría la app como subproceso y le hablaría por `stdin`/`stdout`, lo que implicaría correr un segundo proceso separado del Spring Boot web app que ya existe. Con SSE, el servidor MCP vive en el mismo puerto y `ApplicationContext`, así que la tool llama directo a los beans existentes (`EmbeddingService`, `DocumentChunkRepository`) sin duplicar lógica ni levantar nada aparte.
- **Chunking con LangChain4j en vez de reglas custom**: dividir texto en español respetando párrafos/oraciones (sin cortar una idea a la mitad) y agregando overlap entre fragmentos es un problema ya resuelto por el `DocumentSplitter` recursivo de LangChain4j (corta por párrafo, y si no entra, por oración, y si no entra, por palabra). Escribir esto a mano implicaría reinventar reglas de fin-de-oración en español y manejo de casos límite, sin ninguna ventaja sobre usar una librería que el proyecto ya integra para embeddings. Tamaño (400 caracteres) y overlap (50) quedaron como properties configurables (`rag.chunking.max-chars`, `rag.chunking.overlap-chars`) en vez de hardcodeados.
- **`resumir_chunks` queda como tool MCP independiente, no forzada desde el servidor**: la idea original era que el LLM orquestador la invocara solo después de `buscar_documentos_relevantes` cuando hubiera más de 3 fragmentos. Probado a mano, no fue confiable — con preguntas neutras (ej. "talk me about canaries") el LLM sintetiza la respuesta final él mismo a partir de los chunks crudos y nunca llega a invocar la segunda tool, aun superando ese umbral. Se probó la alternativa de forzarlo en el servidor (que `DocumentSearchTool` llamara directo a la lógica de resumen cuando `chunks.size() > 3`, sin pasar por el LLM) y funcionaba de forma determinística, pero se descartó: la decisión de cuándo sintetizar se quiere dejar en manos del LLM orquestador, aceptando que no siempre la tome.
