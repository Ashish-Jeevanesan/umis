/**
 * API client for communicating with the PO Processor backend service.
 */

import {
  JobPageResponse,
  ProcessingJob,
  PoDashboardSummary,
  JobListParams
} from '../types/po';

const PO_BASE_URL = import.meta.env.VITE_PO_API_BASE_URL || '/po-api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
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

  getHealth: async (): Promise<{ status: string; service: string; timestamp: string }> => {
    return fetchJson<{ status: string; service: string; timestamp: string }>(`${PO_BASE_URL}/health`);
  },
};
