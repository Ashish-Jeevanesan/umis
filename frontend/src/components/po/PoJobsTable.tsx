import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { poApi } from '../../api/poClient';
import { JobListParams, ProcessingJob } from '../../types/po';
import { PoStatusBadge } from './PoStatusBadge';
import {
  Search,
  Filter,
  RefreshCw,
  Calendar,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Paperclip,
  Inbox,
  AlertCircle,
} from 'lucide-react';

export const PoJobsTable: React.FC = () => {
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [page, setPage] = useState(0);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const pageSize = 15;

  // Debounce search input
  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedSearch(search);
      setPage(0);
    }, 400);
    return () => clearTimeout(handler);
  }, [search]);

  const queryParams: JobListParams = {
    page,
    size: pageSize,
    status: statusFilter !== 'ALL' ? statusFilter : undefined,
    search: debouncedSearch.trim() || undefined,
    startDate: startDate ? new Date(startDate).toISOString() : undefined,
    endDate: endDate ? new Date(endDate).toISOString() : undefined,
  };

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['poJobs', queryParams],
    queryFn: () => poApi.listJobs(queryParams),
    refetchInterval: autoRefresh ? 10000 : false,
  });

  const jobs = data?.content || [];
  const totalPages = data?.totalPages || 0;
  const totalElements = data?.totalElements || 0;

  const formatDate = (dateStr: string | null) => {
    if (!dateStr) return '—';
    try {
      const date = new Date(dateStr);
      return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div className="bg-slate-900/60 border border-slate-800 rounded-xl overflow-hidden shadow-sm">
      {/* Controls & Filter Bar */}
      <div className="p-4 border-b border-slate-800/80 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2.5 flex-1 min-w-[280px]">
          {/* Search Input */}
          <div className="relative flex-1 min-w-[200px] max-w-sm">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search by subject, sender..."
              className="w-full bg-slate-950 border border-slate-700/80 rounded-lg pl-9 pr-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-amber-500"
            />
          </div>

          {/* Status Filter */}
          <div className="flex items-center gap-1.5">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
              className="bg-slate-950 border border-slate-700/80 rounded-lg px-2.5 py-1.5 text-xs text-slate-200 focus:outline-none focus:border-amber-500"
            >
              <option value="ALL">All Statuses</option>
              <option value="READY_FOR_EXTRACTION">Ready for Extraction</option>
              <option value="PROCESSING">Processing</option>
              <option value="PENDING">Pending</option>
              <option value="COMPLETED">Completed</option>
              <option value="FAILED">Failed</option>
            </select>
          </div>

          {/* Date Range Inputs */}
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <Calendar className="w-3.5 h-3.5 text-slate-500" />
            <input
              type="date"
              value={startDate}
              onChange={(e) => {
                setStartDate(e.target.value);
                setPage(0);
              }}
              className="bg-slate-950 border border-slate-700/80 rounded-lg px-2 py-1 text-xs text-slate-200"
              title="Start Date"
            />
            <span>to</span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => {
                setEndDate(e.target.value);
                setPage(0);
              }}
              className="bg-slate-950 border border-slate-700/80 rounded-lg px-2 py-1 text-xs text-slate-200"
              title="End Date"
            />
            {(startDate || endDate) && (
              <button
                onClick={() => {
                  setStartDate('');
                  setEndDate('');
                }}
                className="text-[11px] text-slate-400 hover:text-white ml-1 underline"
              >
                Clear
              </button>
            )}
          </div>
        </div>

        {/* Action Controls & Auto-refresh */}
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-1.5 text-xs text-slate-400 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded bg-slate-950 border-slate-700 text-amber-500 focus:ring-0"
            />
            <span>Auto-refresh (10s)</span>
          </label>

          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-200 bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin text-amber-400' : ''}`} />
            <span>Refresh</span>
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {isError && (
        <div className="p-3 bg-rose-500/10 border-b border-rose-500/20 text-rose-300 text-xs flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>Error loading jobs: {error instanceof Error ? error.message : 'Failed to query PO Processor'}</span>
          <button onClick={() => refetch()} className="ml-auto underline font-medium">
            Retry
          </button>
        </div>
      )}

      {/* Table Content */}
      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-800 bg-slate-950/40 text-[11px] text-slate-400 font-semibold uppercase tracking-wider">
              <th className="py-3 px-4">Email Subject & Details</th>
              <th className="py-3 px-4">Sender</th>
              <th className="py-3 px-4">Processing Status</th>
              <th className="py-3 px-4">Attachments</th>
              <th className="py-3 px-4">Attempts</th>
              <th className="py-3 px-4">Received</th>
              <th className="py-3 px-4">Last Updated</th>
              <th className="py-3 px-4 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {isLoading ? (
              Array.from({ length: 5 }).map((_, idx) => (
                <tr key={idx} className="animate-pulse">
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-48 mb-1" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-32" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-24" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-12" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-8" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-20" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-20" /></td>
                  <td className="py-3.5 px-4"><div className="h-4 bg-slate-800 rounded w-16 ml-auto" /></td>
                </tr>
              ))
            ) : jobs.length === 0 ? (
              <tr>
                <td colSpan={8} className="py-12 text-center text-slate-500">
                  <Inbox className="w-8 h-8 mx-auto mb-2 text-slate-600" />
                  <p className="font-medium text-slate-400">No processing jobs found</p>
                  <p className="text-[11px] mt-1">
                    {debouncedSearch || statusFilter !== 'ALL' || startDate || endDate
                      ? 'Try adjusting your filters or search terms.'
                      : 'Jobs will appear here as incoming emails are ingested and delivered over RabbitMQ.'}
                  </p>
                </td>
              </tr>
            ) : (
              jobs.map((job: ProcessingJob) => (
                <tr
                  key={job.id}
                  onClick={() => navigate(`/po/jobs/${job.id}`)}
                  className="hover:bg-slate-800/40 transition-colors cursor-pointer group"
                >
                  <td className="py-3 px-4 font-medium text-white max-w-xs truncate">
                    <div className="truncate group-hover:text-amber-400 transition-colors">
                      {job.emailSubject || `Email ${job.umisEmailId.slice(0, 8)}...`}
                    </div>
                    <div className="text-[10px] text-slate-500 font-mono mt-0.5 truncate">
                      Job ID: {job.id.slice(0, 13)}...
                    </div>
                  </td>
                  <td className="py-3 px-4 text-slate-300 max-w-[180px] truncate">
                    <div className="truncate">{job.emailSenderName || job.emailSender || 'Unknown'}</div>
                    {job.emailSender && job.emailSenderName && (
                      <div className="text-[10px] text-slate-500 truncate">{job.emailSender}</div>
                    )}
                  </td>
                  <td className="py-3 px-4 whitespace-nowrap">
                    <PoStatusBadge status={job.processingStatus} />
                  </td>
                  <td className="py-3 px-4 whitespace-nowrap">
                    <span className="inline-flex items-center gap-1 text-[11px] text-slate-300 bg-slate-800/80 px-2 py-0.5 rounded-md border border-slate-700/60">
                      <Paperclip className="w-3 h-3 text-slate-400" />
                      <span>{job.attachmentCount ?? job.attachments?.length ?? 0}</span>
                    </span>
                  </td>
                  <td className="py-3 px-4 whitespace-nowrap text-slate-400 font-mono text-[11px]">
                    {job.processingAttempts}
                  </td>
                  <td className="py-3 px-4 whitespace-nowrap text-slate-400">
                    {formatDate(job.receivedAt)}
                  </td>
                  <td className="py-3 px-4 whitespace-nowrap text-slate-400">
                    {formatDate(job.updatedAt)}
                  </td>
                  <td className="py-3 px-4 text-right whitespace-nowrap" onClick={(e) => e.stopPropagation()}>
                    <button
                      onClick={() => navigate(`/po/jobs/${job.id}`)}
                      className="inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-medium text-amber-400 hover:text-amber-300 bg-amber-500/10 hover:bg-amber-500/20 border border-amber-500/30 rounded-lg transition-colors"
                    >
                      <span>Details</span>
                      <ExternalLink className="w-3 h-3" />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination Footer */}
      <div className="p-4 border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
        <div>
          Showing <span className="font-semibold text-white">{jobs.length}</span> of{' '}
          <span className="font-semibold text-white">{totalElements}</span> jobs
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => setPage((prev) => Math.max(0, prev - 1))}
            disabled={page === 0 || isLoading}
            className="p-1.5 rounded-lg border border-slate-800 hover:bg-slate-800 text-slate-300 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <ChevronLeft className="w-4 h-4" />
          </button>
          <span>
            Page <span className="font-semibold text-white">{totalPages > 0 ? page + 1 : 1}</span> of{' '}
            <span className="font-semibold text-white">{Math.max(1, totalPages)}</span>
          </span>
          <button
            onClick={() => setPage((prev) => prev + 1)}
            disabled={page + 1 >= totalPages || isLoading}
            className="p-1.5 rounded-lg border border-slate-800 hover:bg-slate-800 text-slate-300 disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};
