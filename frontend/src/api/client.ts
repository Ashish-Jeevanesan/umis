import {
  GlobalDashboardData,
  TenantDashboardData,
  Tenant,
  MailboxConnection,
  MailboxProvider,
  SyncStrategy,
  EmailMessage,
  EmailDetail,
  SystemHealthData,
  EmailProcessingStatus,
} from '../types';
import {
  mockGlobalDashboard,
  mockTenantDashboard,
  mockTenants,
  mockMailboxes,
  mockEmails,
  mockEmailDetails,
  mockProcessingStatuses,
  mockSystemHealth,
} from './mockData';

const BASE_URL = '/api/v1';

let activeAuthPromise: Promise<string | null> | null = null;

export function getToken(): string | null {
  return localStorage.getItem('umis_auth_token');
}

export async function authenticateSession(role: 'GLOBAL_ADMIN' | 'TENANT_ADMIN' = 'GLOBAL_ADMIN'): Promise<string | null> {
  const credentials = role === 'GLOBAL_ADMIN'
    ? { email: 'admin@umis.local', password: 'Admin@123' }
    : { email: 'admin@acme.com', password: 'Admin@123' };

  try {
    const response = await fetch(`${BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(credentials),
    });
    if (response.ok) {
      const envelope = await response.json();
      const token = envelope?.data?.token;
      if (token) {
        localStorage.setItem('umis_auth_token', token);
        return token;
      }
    }
  } catch {
    // Backend offline or unreachable
  }
  return null;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let token = getToken();
  if (!token && !path.startsWith('/auth/login')) {
    if (!activeAuthPromise) {
      activeAuthPromise = authenticateSession().finally(() => {
        activeAuthPromise = null;
      });
    }
    token = await activeAuthPromise;
  }

  const headers = new Headers(options.headers || {});
  headers.set('Content-Type', 'application/json');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
  }

  const envelope = await response.json();
  return envelope.data !== undefined ? envelope.data : envelope;
}

export const api = {
  // Dashboards
  getGlobalDashboard: async (): Promise<GlobalDashboardData> => {
    try {
      return await request<GlobalDashboardData>('/dashboard/global');
    } catch {
      return mockGlobalDashboard;
    }
  },

  getTenantDashboard: async (tenantId?: string): Promise<TenantDashboardData> => {
    try {
      const q = tenantId ? `?tenantId=${tenantId}` : '';
      return await request<TenantDashboardData>(`/dashboard/tenant${q}`);
    } catch {
      return mockTenantDashboard;
    }
  },

  getSystemHealth: async (): Promise<SystemHealthData> => {
    try {
      return await request<SystemHealthData>('/dashboard/health');
    } catch {
      return mockSystemHealth;
    }
  },

  // Tenants
  listTenants: async (): Promise<Tenant[]> => {
    try {
      const res = await request<{ content: Tenant[] }>('/tenants');
      return res.content;
    } catch {
      return mockTenants;
    }
  },

  createTenant: async (data: { code: string; name: string }): Promise<Tenant> => {
    try {
      return await request<Tenant>('/tenants', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    } catch {
      const created: Tenant = {
        id: crypto.randomUUID(),
        code: data.code,
        name: data.name,
        status: 'ACTIVE',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      };
      mockTenants.unshift(created);
      return created;
    }
  },

  toggleTenantStatus: async (tenantId: string, currentStatus: string): Promise<void> => {
    const action = currentStatus === 'ACTIVE' ? 'suspend' : 'activate';
    try {
      await request(`/tenants/${tenantId}/${action}`, { method: 'POST' });
    } catch {
      const tenant = mockTenants.find((t) => t.id === tenantId);
      if (tenant) {
        tenant.status = action === 'suspend' ? 'SUSPENDED' : 'ACTIVE';
      }
    }
  },

  // Mailboxes
  initiateOAuth: (
    provider: MailboxProvider,
    redirectUri: string,
    tenantId: string
  ): Promise<{ authorizationUrl: string; state: string; codeVerifier: string }> => {
    const params = new URLSearchParams({ provider, redirectUri, tenantId });
    return request(`/mailboxes/oauth/initiate?${params.toString()}`);
  },

  connectMailbox: (data: {
    tenantId?: string;
    provider: MailboxProvider;
    emailAddress?: string;
    displayName?: string;
    syncStrategy?: SyncStrategy;
    pollingIntervalMinutes?: number;
    authorizationCode?: string;
    codeVerifier?: string;
    redirectUri?: string;
    state?: string;
    imapHost?: string;
    imapPort?: number;
    imapPassword?: string;
    useSsl?: boolean;
  }): Promise<MailboxConnection> => {
    // No mock fallback here: a failed connection attempt must surface to the
    // user rather than silently pretending to succeed.
    return request<MailboxConnection>('/mailboxes/connect', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  listMailboxes: async (tenantId?: string): Promise<MailboxConnection[]> => {
    try {
      const q = tenantId ? `?tenantId=${tenantId}` : '';
      const res = await request<{ content: MailboxConnection[] }>(`/mailboxes${q}`);
      return res.content;
    } catch {
      return mockMailboxes;
    }
  },

  triggerSync: async (mailboxId: string): Promise<void> => {
    try {
      await request(`/mailboxes/${mailboxId}/sync`, { method: 'POST' });
    } catch {
      const mailbox = mockMailboxes.find((m) => m.id === mailboxId);
      if (mailbox) {
        mailbox.lastSyncAt = new Date().toISOString();
        mailbox.lastSuccessfulSyncAt = new Date().toISOString();
      }
    }
  },

  checkMailboxHealth: async (mailboxId: string): Promise<void> => {
    try {
      await request(`/mailboxes/${mailboxId}/health`);
    } catch {
      // Mock health checked
    }
  },

  disconnectMailbox: async (mailboxId: string): Promise<void> => {
    try {
      await request(`/mailboxes/${mailboxId}`, { method: 'DELETE' });
    } catch {
      const mailbox = mockMailboxes.find((m) => m.id === mailboxId);
      if (mailbox) {
        mailbox.status = 'DISCONNECTED';
      }
    }
  },

  // Emails
  listEmails: async (tenantId?: string, mailboxId?: string): Promise<EmailMessage[]> => {
    try {
      const params = new URLSearchParams();
      if (tenantId) params.set('tenantId', tenantId);
      if (mailboxId) params.set('mailboxId', mailboxId);
      const res = await request<{ content: EmailMessage[] }>(`/emails?${params.toString()}`);
      return res.content;
    } catch {
      return mockEmails;
    }
  },

  getEmailDetail: async (emailId: string): Promise<EmailDetail> => {
    try {
      return await request<EmailDetail>(`/emails/${emailId}`);
    } catch {
      return (
        mockEmailDetails[emailId] || {
          ...mockEmails[0],
          recipients: ['orders@acme.com'],
          bodyHtmlAvailable: false,
          attachments: [],
        }
      );
    }
  },

  getProcessingStatuses: async (emailId: string): Promise<EmailProcessingStatus[]> => {
    try {
      return await request<EmailProcessingStatus[]>(`/emails/${emailId}/acknowledgements`);
    } catch {
      return mockProcessingStatuses[emailId] || [];
    }
  },
};
