/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_UMIS_API_BASE_URL?: string;
  readonly VITE_PO_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
