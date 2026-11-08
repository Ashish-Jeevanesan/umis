/**
 * Types and interfaces for the PO Processor monitoring module.
 */

export type JobProcessingStatus =
  | 'PENDING'
  | 'READY_FOR_EXTRACTION'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | 'EXTRACTION_IN_PROGRESS';

export interface JobAttachment {
  id: string;
  storageReference: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  fileHash: string | null;
  processingStatus: string;
  createdAt: string;
}

export interface ProcessingJob {
  id: string;
  tenantId: string;
  umisEmailId: string;
  mailboxId: string;
  eventId: string;
  emailSubject: string | null;
  emailSender: string | null;
  emailSenderName: string | null;
  emailRecipients: string | null;
  processingStatus: JobProcessingStatus;
  processingAttempts: number;
  attachmentCount: number;
  lastError: string | null;
  receivedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  attachments: JobAttachment[];
}

export interface PoDashboardSummary {
  totalReceived: number;
  pending: number;
  processing: number;
  readyForExtraction: number;
  completed: number;
  failed: number;
}

export interface JobPageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
  first: boolean;
  empty: boolean;
}

export interface JobListParams {
  page?: number;
  size?: number;
  status?: string;
  search?: string;
  startDate?: string;
  endDate?: string;
}
