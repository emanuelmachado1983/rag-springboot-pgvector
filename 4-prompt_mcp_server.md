Quiero agregar un servidor MCP (Model Context Protocol) sobre este mismo proyecto Spring Boot, reutilizando la lógica de búsqueda por similitud que ya existe (la que usa el endpoint GET /api/documents/search contra la tabla document_chunks con pgvector).

## Contexto

Soy backend Java senior aprendiendo MCP por primera vez. Ya entiendo el concepto (cliente MCP = LLM que decide cuándo invocar una tool, servidor MCP = mi código que expone esa tool y ejecuta la lógica real). Lo que necesito es la implementación concreta en Java.

## Lo que necesito

1. **Agregar el servidor MCP al proyecto existente**, no como proyecto nuevo. Necesito que me digas qué dependencia/SDK de Java usar para esto (el SDK oficial de MCP para Java si existe y está maduro, o la alternativa más sólida si no).

2. **Exponer una tool MCP** que reutilice el servicio de búsqueda por similitud que ya existe en el proyecto. La tool debería:
   - Recibir como parámetro una pregunta/query en texto (string).
   - Internamente, usar el mismo servicio que ya tengo para convertir esa query a embedding y buscar los chunks más similares en pgvector.
   - Devolver al LLM los chunks encontrados (contenido + nombre del documento), en el formato que espera el protocolo MCP.
   - Nombrá la tool de forma clara, por ejemplo `buscar_documentos_relevantes` o similar, con una descripción que ayude al LLM a entender cuándo conviene usarla.

3. **No quiero duplicar lógica**: la tool MCP debe llamar al servicio existente (el mismo que usa el controller REST), no reimplementar la búsqueda.

4. **Explicame cómo se levanta y se prueba este servidor MCP**:
   - ¿Corre dentro del mismo proceso de Spring Boot, o necesita un puerto/transporte distinto (stdio vs HTTP/SSE)? Explicame las opciones si las hay, y cuál recomendás para este caso de aprendizaje.
   - ¿Cómo lo conecto como servidor MCP desde Claude Desktop o Claude Code para probarlo manualmente, haciéndole una pregunta y viendo que efectivamente invoca la tool?

5. **Mantené el endpoint REST actual intacto** — el objetivo es que ambas formas de acceso (REST para humanos, MCP para LLMs) convivan, ambas usando el mismo servicio de búsqueda por debajo.

## Importante

- Si hay alguna decisión de diseño con más de una opción razonable (por ejemplo, transporte stdio vs HTTP, o cómo estructurar el módulo MCP dentro del proyecto), explicame las opciones y tu recomendación antes de asumir, igual que en los pasos anteriores del proyecto.
- Mantené todo simple, es un proyecto de aprendizaje. No hace falta autenticación ni seguridad avanzada en esta etapa, pero si hay algo elemental de seguridad que no debería saltearse (por ejemplo, no exponer la tool sin ningún control en una red abierta), mencionámelo brevemente.
- Mostrame los archivos nuevos/modificados y los pasos para probarlo end-to-end.
