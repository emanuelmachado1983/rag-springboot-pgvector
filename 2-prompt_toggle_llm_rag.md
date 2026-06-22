Quiero extender el endpoint de búsqueda existente (GET /api/documents/search) para agregar la capa de "generación" de RAG, pero necesito que sea totalmente activable/desactivable mediante una property, sin romper el comportamiento actual.

## Comportamiento esperado

Agregá una property en application.properties, por ejemplo:

```
rag.llm.enabled=false
```

- Si `rag.llm.enabled=false` (o no está seteada): el endpoint /api/documents/search se comporta exactamente igual que ahora — devuelve los chunks crudos más similares, sin llamar a ningún LLM.
- Si `rag.llm.enabled=true`: el endpoint, además de buscar los chunks por similitud, los usa como contexto, arma un prompt, llama a la API de Claude (Anthropic), y devuelve la respuesta generada por el LLM en lugar del chunk pelado (o además del chunk, a tu criterio de diseño — explicame las opciones).

## Detalles técnicos

1. **Cliente de la API de Anthropic**: usá el SDK oficial de Java de Anthropic, o si LangChain4j ya tiene soporte integrado para Claude (chat model), preferí esa opción ya que el proyecto ya usa LangChain4j para los embeddings — así mantenemos consistencia.

2. **API key**: debe leerse desde una variable de entorno o property externa (NUNCA hardcodeada en el código ni en application.properties versionado). Dejame una property tipo:
   ```
   anthropic.api.key=${ANTHROPIC_API_KEY:}
   ```
   Y si `rag.llm.enabled=true` pero la key está vacía, que el sistema lo detecte al arrancar y tire un error claro (fail-fast), no un fallo silencioso en runtime.

3. **Modelo a usar**: Claude Haiku (el más económico), dejá el nombre del modelo como property también, así puedo cambiarlo fácil:
   ```
   anthropic.model=claude-haiku-4-5-20251001
   ```

4. **Prompt template**: armá un prompt simple tipo:
   ```
   Contexto: {chunks recuperados, concatenados}
   
   Pregunta del usuario: {pregunta}
   
   Respondé la pregunta usando solo la información del contexto. Si el contexto no tiene información suficiente para responder, decilo explícitamente.
   ```

5. **Arquitectura**: separá la lógica en un servicio nuevo (por ejemplo `RagAnswerService` o similar) que dependa del servicio de búsqueda existente, en vez de meter todo en el controller. El controller debería decidir, según la property `rag.llm.enabled`, si llama solo al servicio de búsqueda (comportamiento actual) o también al nuevo servicio de generación.

6. **Manejo de errores**: si la llamada a la API de Claude falla (sin crédito, sin conexión, rate limit, etc.), que el endpoint no rompa — debería loguear el error y devolver como fallback los chunks crudos (el comportamiento actual), con algún indicador en la respuesta de que la generación falló y se devolvió el modo básico.

## Importante

- No toques ni rompas el comportamiento actual cuando `rag.llm.enabled=false` — es el modo en el que voy a seguir trabajando hasta que cargue crédito en la cuenta de API de Anthropic.
- Si hay más de una forma razonable de implementar algo (por ejemplo, dónde poner el toggle, cómo estructurar la respuesta del endpoint cuando hay LLM vs cuando no), explicame las opciones y tu recomendación antes de asumir.
- Mantené todo simple, es un proyecto de aprendizaje, no algo productivo.

¿Podés implementar esto, mostrarme los archivos modificados/nuevos, y explicarme cómo activar/desactivar la funcionalidad cuando tenga la API key lista?
