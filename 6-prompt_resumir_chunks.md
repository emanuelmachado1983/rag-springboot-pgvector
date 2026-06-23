Contexto: tengo un proyecto RAG en Spring Boot + pgvector + LangChain4j 
(repo rag-springboot-pgvector). Ya tiene:
- RAG funcionando (embeddings locales + búsqueda por similitud coseno en pgvector).
- Chunking inteligente con DocumentSplitters de LangChain4j.
- Un servidor MCP (spring-ai-starter-mcp-server-webmvc, transporte SSE) que 
  expone una tool `buscar_documentos_relevantes` (@Tool + @ToolParam), 
  probada con éxito desde Claude Code.

Objetivo: agregar una segunda tool MCP, `resumir_chunks`, para demostrar 
function calling encadenado (agentic): el LLM orquestador debe poder 
decidir, después de buscar, si conviene resumir los resultados antes de 
generar la respuesta final.

Quiero que la tool `resumir_chunks`:
1. Reciba como parámetro una lista de chunks (texto + metadata mínima: 
   documentName, content) — los mismos que devuelve `buscar_documentos_relevantes`.
2. Genere un resumen compacto combinando esos chunks en un solo texto, 
   eliminando redundancia entre ellos.
3. Para la generación del resumen, reutilizá el mismo AnthropicChatModel 
   que ya tengo condicionado a `rag.llm.enabled=true` (no quiero duplicar 
   configuración de cliente Claude). Si `rag.llm.enabled=false`, esta tool 
   debe devolver un mensaje claro indicando que requiere LLM activado, en 
   vez de fallar feo.
4. Anotada igual que la tool existente (@Tool + @ToolParam), reutilizando 
   el mismo estilo y convenciones que ya usaste para `buscar_documentos_relevantes`.

No quiero que la app decida automáticamente cuándo encadenar — la idea es 
que sea el LLM orquestador (Claude Code u otro cliente MCP) el que decida 
si llama solo a `buscar_documentos_relevantes`, o además llama a 
`resumir_chunks` después, según el caso. Mi servidor solo expone ambas 
tools independientes.

Antes de programar, explicame:
- Cómo vas a estructurar el prompt interno para el resumen (qué instrucciones 
  le das a Claude para que resuma sin alucinar ni agregar info que no está 
  en los chunks).
- Si conviene que la tool devuelva solo texto plano, o un objeto con el 
  resumen + metadata de qué documentos se usaron.

Después de implementarlo, quiero un ejemplo de cómo probarlo manualmente 
desde Claude Code: hacer una pregunta que dispare ambas tools en secuencia 
(buscar y después resumir), y ver cómo decide el orquestador.