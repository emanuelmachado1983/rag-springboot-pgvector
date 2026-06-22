# RAG App — búsqueda semántica sobre documentos propios

Sistema RAG (Retrieval Augmented Generation) 100% en stack Java, hecho como proyecto de aprendizaje y portfolio. Permite cargar fragmentos de texto ("chunks"), generarles un embedding local, guardarlos en Postgres con `pgvector`, y después buscar los más parecidos semánticamente a una pregunta. Opcionalmente (desactivado por default), puede usar un LLM (Claude) para generar una respuesta en lenguaje natural a partir de esos chunks.

## Arquitectura

```
                 POST /api/documents
                        |
                        v
                EmbeddingService  (LangChain4j, modelo local all-MiniLM-L6-v2, 384 dims)
                        |
                        v
              DocumentChunkRepository  (JDBC -> Postgres + pgvector)
                        |
                        v
                  document_chunks  (tabla con columna "vector(384)")


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
```

## Stack

- Java 21, Maven (wrapper incluido, no hace falta tener Maven instalado).
- Spring Boot 3.5.x (`spring-boot-starter-web`, `spring-boot-starter-jdbc`).
- PostgreSQL + extensión [pgvector](https://github.com/pgvector/pgvector), corriendo en Docker.
- [LangChain4j](https://docs.langchain4j.dev/) para:
  - Embeddings locales (`langchain4j-embeddings-all-minilm-l6-v2`, modelo ONNX embebido en el jar, corre en CPU, sin llamadas externas).
  - Cliente de Claude/Anthropic (`langchain4j-anthropic`), usado solo si se activa la capa de LLM.
- [`com.pgvector:pgvector`](https://github.com/pgvector/pgvector-java): helper oficial para bindear `float[]` al tipo `vector` de Postgres desde JDBC.

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

```bash
curl -X POST http://localhost:8082/api/documents \
  -H "Content-Type: application/json" \
  -d '{"documentName":"animales.txt","content":"El gato es un animal domestico muy popular como mascota"}'
```

```json
{ "id": 1 }
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
