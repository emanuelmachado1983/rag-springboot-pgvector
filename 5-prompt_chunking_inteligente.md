Contexto: tengo un proyecto RAG en Spring Boot + pgvector + LangChain4j 
(repo rag-springboot-pgvector). Hoy la ingestión guarda el texto que recibo 
tal cual en `document_chunks.content`, sin dividirlo. Quiero implementar 
chunking inteligente antes de generar el embedding.

Objetivo:
1. Crear un servicio de chunking que reciba un texto largo y lo divida en 
   chunks de ~200-400 tokens (o caracteres, si es más simple para arrancar), 
   respetando límites de párrafo/oración (no cortar una idea a la mitad).
2. Agregar overlap entre chunks consecutivos (ej: la última oración de un 
   chunk se repite como primera oración del siguiente), para no perder 
   contexto en el borde.
3. Modificar el endpoint de ingestión (`POST /api/documents`) para que, en 
   vez de guardar el content completo como un solo chunk, lo pase por este 
   nuevo chunking service y guarde múltiples filas en document_chunks 
   (una por chunk resultante), cada una con su propio embedding.
4. No quiero romper compatibilidad: si me llega un texto corto (menor al 
   tamaño de un chunk), debe seguir funcionando como un único chunk, igual 
   que hoy.

Antes de programar, quiero que me expliques:
- Qué estrategia de chunking conviene para textos en español sin estructura 
  de secciones marcada (solo párrafos sueltos) — por tamaño fijo con overlap, 
  por oración, por párrafo, o combinada.
- Si conviene usar alguna librería de LangChain4j para esto (sé que tiene 
  DocumentSplitter) o si es mejor algo custom simple, y por qué.
- Cómo decidiste el tamaño de chunk y el % de overlap.

Después de la implementación, quiero un endpoint o método de prueba para 
ver cómo quedó dividido un texto largo (lista de chunks generados, sin 
guardarlos en la base), para poder revisar antes de ingerir de verdad.