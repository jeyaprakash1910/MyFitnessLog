/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend REST API base URL including the /api/v1 prefix. See src/config/env.ts. */
  readonly VITE_API_BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
