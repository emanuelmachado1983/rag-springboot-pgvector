El proyecto ya está funcionalmente terminado para esta etapa: ingestión de documentos, embeddings locales con LangChain4j, búsqueda por similitud con pgvector, y una capa de generación con LLM (Claude API) ya implementada pero desactivada por property (rag.llm.enabled=false), lista para activarse en el futuro.

Quiero dejarlo documentado y prolijo como proyecto de portfolio (la parte de subirlo a Git la manejo yo aparte). Necesito:

## 1. README.md completo

Que incluya:
- Descripción breve del proyecto: qué es, qué problema resuelve (sistema RAG aplicado a búsqueda semántica sobre documentos propios).
- Arquitectura general: un resumen del flujo (ingestión → embedding → pgvector → búsqueda → opcionalmente LLM), no hace falta diagrama, alcanza con una descripción clara en texto o un diagrama ASCII simple si te resulta natural.
- Stack tecnológico usado (Java, Spring Boot, LangChain4j, pgvector/PostgreSQL, Docker).
- Instrucciones de instalación y ejecución desde cero: cómo levantar el contenedor de Postgres con pgvector, cómo correr la app, variables de entorno necesarias.
- Ejemplos de uso de los endpoints (POST /api/documents y GET /api/documents/search) con curl o ejemplos de request/response.
- Una sección explicando cómo activar la capa de LLM (rag.llm.enabled=true) el día que se quiera usar, incluyendo qué variable de entorno hace falta (ANTHROPIC_API_KEY) y qué modelo se usa por defecto.
- Una sección breve de "decisiones de diseño" explicando por qué se eligió embeddings locales en vez de API paga, y por qué la capa de LLM es opcional/desactivable.

## 2. Actualizar CLAUDE.md

Actualizá el archivo CLAUDE.md existente para que refleje el estado actual real del proyecto (ya no es un proyecto por armar, sino uno funcionando), incluyendo:
- Qué partes están implementadas y funcionando.
- Qué decisiones de arquitectura ya se tomaron y por qué (para que quede como memoria del proyecto, útil si en el futuro yo u otra persona retoma el desarrollo con ayuda de Claude Code).
- Qué queda pendiente o como posible próximo paso (por ejemplo: activar el LLM, agregar MCP, mejorar el chunking de documentos, etc. — sugerime 2 o 3 ideas de próximos pasos razonables).

No hace falta que cambies la lógica funcional del proyecto, esto es solo documentación. Mostrame los archivos finales (README.md y CLAUDE.md).
