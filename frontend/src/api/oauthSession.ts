import { MailboxProvider } from '../types';

const STORAGE_KEY = 'umis_pending_oauth';

export interface PendingOAuthConnection {
  tenantId: string;
  provider: MailboxProvider;
  codeVerifier: string;
  redirectUri: string;
  state: string;
  displayName?: string;
}

export function savePendingOAuth(pending: PendingOAuthConnection): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pending));
}

export function consumePendingOAuth(): PendingOAuthConnection | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  sessionStorage.removeItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PendingOAuthConnection;
  } catch {
    return null;
  }
}
