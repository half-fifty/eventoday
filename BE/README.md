# Eventoday Backend

## RAG Policy Search Database Prerequisites

The RAG phase 1 Flyway migration creates the policy vector table at application startup. Because Flyway runs independently of the `ai.rag.enabled` feature toggle, the application database must be PostgreSQL with pgvector support even when `ai.rag.enabled=false`.

Required PostgreSQL extensions:

- `vector`
- `hstore`
- `uuid-ossp`

Current development and test baseline:

- PostgreSQL major version: 18
- Testcontainers image: `pgvector/pgvector:pg18`

`ai.rag.enabled=false` disables only the RAG Spring beans, embedding model, policy retrieval, and ingestion execution. It does not remove the database prerequisite created by the Flyway migration.

`AI_RAG_API_KEY` is required only when RAG is enabled. Policy embedding ingestion runs only when both RAG is enabled and `AI_RAG_INGESTION_ENABLED=true`.

Do not write real secret values in repository documentation or example environment files.
