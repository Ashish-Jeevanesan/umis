export type TenantStatus = 'ACTIVE' | 'SUSPENDED';

export interface Tenant {
  id: string;
  code: string;
  name: string;
  status: TenantStatus;
  createdAt: string;
  updatedAt: string;
}

export type MailboxProvider = 'MOCK' | 'MICROSOFT_365' | 'GMAIL' | 'GENERIC_IMAP' | 'UNIFIED_API';

export type MailboxStatus =
  | 'CONNECTED'
  | 'PENDING_AUTHORIZATION'
  | 'SYNCHRONIZING'
  | 'ACTIVE'
  | 'AUTH_EXPIRED'
  | 'CONNECTION_ERROR'
  | 'DISCONNECTED'
  | 'SUSPENDED';

export type SyncStrategy = 'POLLING' | 'WEBHOOK' | 'HYBRID';

export interface MailboxConnection {
  id: string;
  tenantId: string;
  emailAddress: string;
  displayName?: string;
  provider: MailboxProvider;
  status: MailboxStatus;
  syncStrategy: SyncStrategy;
  pollingIntervalMinutes: number;
  lastSyncAt?: string;
  lastSuccessfulSyncAt?: string;
  errorMessage?: string;
  failureCount: number;
  createdAt: string;
}

export type IngestionStatus =
  | 'DISCOVERED'
  | 'FETCHING'
  | 'INGESTED'
  | 'PUBLISHED'
  | 'FAILED'
  | 'DUPLICATE'
  | 'IGNORED';

export interface EmailAttachment {
  id: string;
  emailId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  fileHash: string;
  storageReference: string;
  scanStatus: string;
  createdAt: string;
}

export interface EmailMessage {
  id: string;
  tenantId: string;
  mailboxId: string;
  mailboxEmail: string;
  provider: MailboxProvider;
  providerMessageId: string;
  sender: string;
  senderName?: string;
  subject: string;
  snippet?: string;
  hasAttachments: boolean;
  attachmentCount: number;
  ingestionStatus: IngestionStatus;
  receivedAt: string;
  createdAt: string;
}

export interface EmailDetail extends EmailMessage {
  recipients: string[];
  bodyTextStorageRef?: string;
  bodyHtmlStorageRef?: string;
  bodyHtmlAvailable: boolean;
  attachments: EmailAttachment[];
}

export type ProcessingStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUCCESS'
  | 'FAILED'
  | 'REVIEW_REQUIRED'
  | 'IGNORED';

export interface EmailProcessingStatus {
  id: string;
  emailId: string;
  consumerCode: string;
  status: ProcessingStatus;
  referenceId?: string;
  message?: string;
  processedAt?: string;
  updatedAt: string;
}

export interface DailyTrend {
  date: string;
  discovered: number;
  ingested: number;
  published: number;
  parsedSuccess: number;
  failed: number;
}

export interface ProviderDistribution {
  provider: MailboxProvider;
  displayName: string;
  mailboxCount: number;
  emailsIngested: number;
}

export interface GlobalDashboardData {
  totalTenants: number;
  activeTenants: number;
  connectedMailboxes: number;
  totalEmailsDiscovered: number;
  totalEmailsIngested: number;
  totalEmailsPublished: number;
  emailsParsedSuccess: number;
  emailsFailed: number;
  providerDistribution: ProviderDistribution[];
  recentTenants: Tenant[];
  recentTrends: DailyTrend[];
}

export interface TenantDashboardData {
  tenantId: string;
  tenantName: string;
  connectedMailboxes: number;
  emailsDiscovered: number;
  emailsPicked: number;
  emailsIngested: number;
  emailsPublished: number;
  emailsParsedSuccess: number;
  emailsPending: number;
  emailsFailed: number;
  mailboxes: MailboxConnection[];
  trends: DailyTrend[];
}

export interface SystemHealthData {
  status: 'UP' | 'DEGRADED' | 'DOWN';
  timestamp: string;
  databaseConnected: boolean;
  rabbitMqConnected: boolean;
  storageConnected: boolean;
  pendingOutboxCount: number;
  failedMailboxesCount: number;
  componentDetails: Record<string, string>;
}

export interface UserPrincipal {
  userId: string;
  tenantId?: string;
  email: string;
  role: 'GLOBAL_ADMIN' | 'TENANT_ADMIN' | 'TENANT_VIEWER' | 'SERVICE_CLIENT';
  tenantName?: string;
  tenantCode?: string;
}
