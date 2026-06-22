Armame el proyecto Spring Boot inicial para el sistema RAG descripto en CLAUDE.md.

Necesito:

1. Proyecto Spring Boot con las dependencias necesarias (Spring Web, Spring Data JPA, driver de PostgreSQL, LangChain4j con el módulo de embeddings locales).
2. Configuración de conexión a la base en application.properties/yml.
3. Entidad/repositorio para `document_chunks` (decidí si conviene JPA puro o JDBC nativo para el campo vector, y explicame por qué).
4. Un servicio de embeddings que reciba un texto y devuelva el vector de 384 dimensiones usando el modelo local.
5. Endpoint POST /api/documents para ingestión (genera embedding y guarda).
6. Endpoint GET /api/documents/search?query=... para búsqueda por similitud coseno con pgvector, devolviendo los N chunks más relevantes.

Generá la estructura completa del proyecto, el código de cada archivo, y los pasos para compilar y correrlo.
