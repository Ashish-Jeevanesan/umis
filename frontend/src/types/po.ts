/**
 * Types and interfaces for the PO Processor AI Intelligence Engine.
 */

export type JobProcessingStatus =
  | 'PENDING'
  | 'QUEUED'
  | 'READY_FOR_EXTRACTION'
  | 'PROCESSING'
  | 'READY_FOR_REVIEW'
  | 'DUPLICATE'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | 'EXTRACTION_IN_PROGRESS';

export type PoWorkflowStatus =
  | 'DRAFT'
  | 'READY_FOR_REVIEW'
  | 'NEEDS_REVIEW'
  | 'EDITED'
  | 'VERIFIED'
  | 'APPROVED';

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
  progressPercentage?: number;
  currentProcessingStage?: string;
  progressMessage?: string;
  attachments: JobAttachment[];
}

export interface PoDashboardSummary {
  totalReceived: number;
  poIdentified: number;
  readyForReview: number;
  needsReview: number;
  verified: number;
  approved: number;
  duplicate: number;
  failed: number;
  pending?: number;
  processing?: number;
  readyForExtraction?: number;
  completed?: number;
}

export interface ExtractedPurchaseOrder {
  id: string;
  processingJobId: string;
  tenantId: string;
  hospitalId?: string | null;
  hospitalName?: string | null;
  hospitalCode?: string | null;
  hospitalLocationName?: string | null;
  hospital?: {
    id: string;
    normalizedName: string;
    legalName: string | null;
    hospitalCode: string | null;
  } | null;
  poNumber: string;
  poTitleDescription: string | null;
  poDate: string | null;
  requestedDeliveryDate: string | null;
  expectedDeliveryDate: string | null;
  currency: string;
  subtotal: number | null;
  discount: number | null;
  freightCharges: number | null;
  taxAmount: number | null;
  taxPercentage: number | null;
  grandTotal: number;
  paymentTerms: string | null;
  paymentMethod: string | null;
  deliveryInstructions: string | null;
  incoterms: string | null;
  contractReferenceNumber: string | null;
  quotationReference: string | null;
  workflowStatus: PoWorkflowStatus;
  vendorName: string | null;
  vendorAddress: string | null;
  vendorContact: string | null;
  vendorEmail: string | null;
  vendorPhone: string | null;
  vendorTaxId: string | null;
  vendorAccountNumber: string | null;
  isApproved: boolean;
  approvedBy: string | null;
  approvedAt: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
  revisionNumber?: number;
  parentPoId?: string | null;
}

export interface ExtractedPoLineItem {
  id: string;
  lineNumber: number;
  productCode: string | null;
  mfgPartNumber: string | null;
  productName: string | null;
  description: string | null;
  category: string | null;
  quantity: number;
  unit: string | null;
  unitPrice: number;
  discount: number | null;
  taxPercentage: number | null;
  taxAmount: number | null;
  lineSubtotal: number | null;
  lineTotal: number;
  currency: string | null;
  batchLotReference: string | null;
  serialNumber: string | null;
}

export interface PoPatientDetail {
  id: string;
  patientName: string | null;
  patientIdMrn: string | null;
  patientRefNumber: string | null;
  dateOfBirth: string | null;
  procedureCaseReference: string | null;
  surgeonDoctorName: string | null;
  departmentSpecialty: string | null;
}

export interface PoBillingDetail {
  id: string;
  billingEntity: string | null;
  billingContact: string | null;
  billingAddress: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  country: string | null;
  taxId: string | null;
}

export interface PoShippingDetail {
  id: string;
  shipToEntity: string | null;
  hospitalLocationName: string | null;
  shippingContact: string | null;
  shippingAddress: string | null;
  city: string | null;
  stateRegion: string | null;
  postalCode: string | null;
  country: string | null;
  deliveryInstructions: string | null;
  requestedDeliveryDate: string | null;
}

export interface PoDocumentAnalysis {
  id: string;
  originalFilename: string;
  mimeType: string | null;
  fileSize: number | null;
  fileHash: string | null;
  pageCount: number;
  documentClassification: string;
  extractionMethod: string;
  extractionEngine: string | null;
  ocrUsed: boolean;
  processingResult: string;
  extractedTextPreview: string | null;
}

export interface FieldComparisonItem {
  fieldName: string;
  emailValue: string;
  attachmentValue: string;
  matchResult: 'MATCH' | 'MISMATCH' | 'MISSING_IN_EMAIL' | 'MISSING_IN_ATTACHMENT';
  comments: string;
}

export interface PoBodyAttachmentComparison {
  id: string;
  comparisonStatus: string;
  comparisonReport: string;
  conflictCount: number;
}

export interface PoValidationResult {
  id: string;
  ruleCode: string;
  ruleName: string;
  severity: 'ERROR' | 'WARNING' | 'INFO';
  passed: boolean;
  expectedValue: string | null;
  actualValue: string | null;
  explanation: string | null;
}

export interface PoDuplicateDetection {
  id: string;
  duplicateType: string;
  detectionReason: string;
  confidenceScore: number;
  reviewStatus: string;
}

export interface PoFieldEditHistory {
  id: string;
  fieldName: string;
  originalValue: string | null;
  newValue: string | null;
  editedBy: string;
  editedAt: string;
  editReason: string | null;
}

export interface PoApprovalHistory {
  id: string;
  action: string;
  actionBy: string;
  actionAt: string;
  comments: string | null;
}

export interface HospitalHistory {
  hospitalId: string;
  hospitalName: string;
  legalName: string | null;
  hospitalCode: string | null;
  totalApprovedPoCount: number;
  approvedTotalsByCurrency: Record<string, number>;
  firstPoReceivedDate: string | null;
  latestPoReceivedDate: string | null;
  historicalPurchaseOrders: Array<{
    poId: string;
    poNumber: string;
    poDate: string | null;
    grandTotal: number;
    currency: string;
    vendorName: string | null;
    isApproved: boolean;
  }>;
}

export interface ProcessingJobFullDetails {
  job: ProcessingJob;
  purchaseOrder: ExtractedPurchaseOrder | null;
  lineItems: ExtractedPoLineItem[] | null;
  patientDetail: PoPatientDetail | null;
  billingDetail: PoBillingDetail | null;
  shippingDetail: PoShippingDetail | null;
  documentAnalyses: PoDocumentAnalysis[];
  comparison: PoBodyAttachmentComparison | null;
  validationResults: PoValidationResult[] | null;
  duplicateDetections: PoDuplicateDetection[];
  editHistory: PoFieldEditHistory[] | null;
  approvalHistory: PoApprovalHistory[] | null;
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

export interface JobProgressDto {
  jobId: string;
  status: string;
  stage: string;
  progressPercentage: number;
  message: string;
  attempt: number;
  startedAt: string | null;
  updatedAt: string | null;
  completedAt: string | null;
  lastError: string | null;
}
