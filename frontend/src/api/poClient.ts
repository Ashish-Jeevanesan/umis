/**
 * API client for communicating with the PO Processor backend service.
 */

import {
  JobPageResponse,
  ProcessingJob,
  PoDashboardSummary,
  JobListParams,
  ProcessingJobFullDetails,
  HospitalHistory,
  JobProgressDto
} from '../types/po';

const PO_BASE_URL = import.meta.env.VITE_PO_API_BASE_URL || '/po-api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('umis_auth_token');
  const authHeaders: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {};

  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders,
      ...options?.headers,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    let errorMessage = `HTTP ${response.status}: ${response.statusText}`;
    try {
      const errorJson = JSON.parse(errorText);
      if (errorJson.message) {
        errorMessage = errorJson.message;
      }
    } catch {
      // Use fallback error message
    }
    throw new Error(errorMessage);
  }

  return response.json();
}

export const poApi = {
  getSummary: async (): Promise<PoDashboardSummary> => {
    return fetchJson<PoDashboardSummary>(`${PO_BASE_URL}/dashboard/summary`);
  },

  listJobs: async (params: JobListParams = {}): Promise<JobPageResponse<ProcessingJob>> => {
    const query = new URLSearchParams();
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 15));

    if (params.status && params.status !== 'ALL') {
      query.set('status', params.status);
    }
    if (params.search && params.search.trim()) {
      query.set('search', params.search.trim());
    }
    if (params.startDate) {
      query.set('startDate', params.startDate);
    }
    if (params.endDate) {
      query.set('endDate', params.endDate);
    }

    return fetchJson<JobPageResponse<ProcessingJob>>(`${PO_BASE_URL}/processing-jobs?${query.toString()}`);
  },

  getJobById: async (jobId: string): Promise<ProcessingJob> => {
    return fetchJson<ProcessingJob>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}`);
  },

  getJobFullDetails: async (jobId: string): Promise<ProcessingJobFullDetails> => {
    return fetchJson<ProcessingJobFullDetails>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/details`);
  },

  updateField: async (
    jobId: string,
    fieldName: string,
    newValue: string,
    reason?: string
  ): Promise<{ message: string }> => {
    return fetchJson<{ message: string }>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/fields`, {
      method: 'PUT',
      body: JSON.stringify({ fieldName, newValue, reason }),
    });
  },

  verifyJob: async (jobId: string, comments?: string): Promise<{ message: string }> => {
    return fetchJson<{ message: string }>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/verify`, {
      method: 'POST',
      body: JSON.stringify({ comments }),
    });
  },

  approveJob: async (jobId: string, comments?: string): Promise<{ message: string }> => {
    return fetchJson<{ message: string }>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/approve`, {
      method: 'POST',
      body: JSON.stringify({ comments }),
    });
  },

  rejectJob: async (jobId: string, comments?: string): Promise<{ message: string }> => {
    return fetchJson<{ message: string }>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/reject`, {
      method: 'POST',
      body: JSON.stringify({ comments }),
    });
  },

  reExtractJob: async (jobId: string): Promise<{ message: string }> => {
    return fetchJson<{ message: string }>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/re-extract`, {
      method: 'POST',
    });
  },

  getProgress: async (jobId: string): Promise<JobProgressDto> => {
    return fetchJson<JobProgressDto>(`${PO_BASE_URL}/processing-jobs/${encodeURIComponent(jobId)}/progress`);
  },

  getHospitalHistory: async (hospitalId: string, tenantId?: string): Promise<HospitalHistory> => {
    const query = tenantId ? `?tenantId=${encodeURIComponent(tenantId)}` : '';
    return fetchJson<HospitalHistory>(`${PO_BASE_URL}/hospitals/${encodeURIComponent(hospitalId)}/history${query}`);
  },

  getHealth: async (): Promise<{ status: string; service: string; timestamp: string }> => {
    return fetchJson<{ status: string; service: string; timestamp: string }>(`${PO_BASE_URL}/health`);
  },
};
