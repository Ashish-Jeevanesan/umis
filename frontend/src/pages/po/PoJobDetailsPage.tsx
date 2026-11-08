import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { poApi } from '../../api/poClient';
import { PoStatusBadge } from '../../components/po/PoStatusBadge';
import {
  ArrowLeft,
  RefreshCw,
  Mail,
  Paperclip,
  Activity,
  CheckCircle2,
  AlertTriangle,
  Send,
  FileText,
  Copy,
  Hash,
} from 'lucide-react';

export const PoJobDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const {
    data: job,
    isLoading,
    isError,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['poJob', id],
    queryFn: () => poApi.getJobById(id!),
    enabled: Boolean(id),
  });

  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return '—';
    try {
      return new Date(dateStr).toLocaleString('en-US', {
        dateStyle: 'medium',
        timeStyle: 'medium',
      });
    } catch {
      return dateStr;
    }
  };

  const formatFileSize = (bytes: number | null | undefined) => {
    if (!bytes || bytes <= 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const idx = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
    return `${(bytes / Math.pow(1024, idx)).toFixed(1)} ${units[idx]}`;
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  if (isLoading) {
    return (
      <div className="flex-1 p-6 flex items-center justify-center">
        <div className="flex items-center gap-2 text-sm text-slate-400">
          <RefreshCw className="w-4 h-4 animate-spin text-amber-400" />
          <span>Loading processing job details...</span>
        </div>
      </div>
    );
  }

  if (isError || !job) {
    return (
      <div className="flex-1 p-6 max-w-4xl mx-auto">
        <button
          onClick={() => navigate('/po')}
          className="flex items-center gap-2 text-xs font-semibold text-slate-400 hover:text-white mb-6"
        >
          <ArrowLeft className="w-4 h-4" />
          <span>Back to Jobs</span>
        </button>
        <div className="p-6 rounded-xl border border-rose-500/20 bg-rose-500/10 text-rose-300">
          <h2 className="text-sm font-semibold mb-1">Job Not Found or Inaccessible</h2>
          <p className="text-xs text-rose-400/80">
            {error instanceof Error ? error.message : `Unable to load processing job with ID: ${id}`}
          </p>
          <button
            onClick={() => refetch()}
            className="mt-4 px-3 py-1.5 bg-rose-500/20 hover:bg-rose-500/30 text-rose-200 text-xs rounded-lg font-medium"
          >
            Retry Query
          </button>
        </div>
      </div>
    );
  }

  return (
    <main className="flex-1 overflow-y-auto p-6">
      <div className="max-w-6xl mx-auto space-y-6">
        {/* Navigation & Header Bar */}
        <div className="flex flex-wrap items-center justify-between gap-4 pb-4 border-b border-slate-800">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/po')}
              className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
              title="Return to Jobs List"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div>
              <div className="flex items-center gap-3">
                <h1 className="text-lg font-bold text-white tracking-tight">
                  {job.emailSubject || `PO Processing Job ${job.id.slice(0, 8)}`}
                </h1>
                <PoStatusBadge status={job.processingStatus} size="md" />
              </div>
              <p className="text-xs text-slate-400 font-mono mt-1">
                Job ID: {job.id}
                <button
                  onClick={() => copyToClipboard(job.id)}
                  className="ml-2 hover:text-white text-slate-500"
                  title="Copy Job ID"
                >
                  <Copy className="w-3 h-3 inline" />
                </button>
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => refetch()}
              disabled={isFetching}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-200 bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-colors"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin text-amber-400' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Error Banner if job failed */}
        {job.lastError && (
          <div className="p-4 rounded-xl border border-rose-500/30 bg-rose-500/10 text-rose-300 text-xs">
            <div className="flex items-center gap-2 font-semibold text-rose-200 mb-1">
              <AlertTriangle className="w-4 h-4 text-rose-400" />
              <span>Processing Error Encountered</span>
            </div>
            <p className="font-mono text-[11px] text-rose-300/90 whitespace-pre-wrap">{job.lastError}</p>
          </div>
        )}

        {/* 2-Column Info Layout */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Card 1: Email Information */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-800/80 text-white font-semibold text-sm">
              <Mail className="w-4 h-4 text-sky-400" />
              <span>Ingested Email Information</span>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <span className="text-slate-500 block text-[10px] uppercase font-semibold">Subject</span>
                <span className="text-slate-200 font-medium">{job.emailSubject || '—'}</span>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Sender</span>
                  <span className="text-slate-200 font-mono text-[11px] truncate block">
                    {job.emailSender || '—'}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Sender Display Name</span>
                  <span className="text-slate-200">{job.emailSenderName || '—'}</span>
                </div>
              </div>
              <div>
                <span className="text-slate-500 block text-[10px] uppercase font-semibold">Recipients</span>
                <span className="text-slate-300 font-mono text-[11px]">{job.emailRecipients || '—'}</span>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-800/60">
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">UMIS Email ID</span>
                  <span className="text-slate-400 font-mono text-[11px] truncate block" title={job.umisEmailId}>
                    {job.umisEmailId}
                  </span>
                </div>
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Event ID</span>
                  <span className="text-slate-400 font-mono text-[11px] truncate block" title={job.eventId}>
                    {job.eventId}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* Card 2: Processing Lifecycle & Timestamps */}
          <div className="bg-slate-900/60 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex items-center gap-2 pb-3 border-b border-slate-800/80 text-white font-semibold text-sm">
              <Activity className="w-4 h-4 text-amber-400" />
              <span>Processing Lifecycle</span>
            </div>

            <div className="space-y-3 text-xs">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Current Status</span>
                  <div className="mt-1">
                    <PoStatusBadge status={job.processingStatus} />
                  </div>
                </div>
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Processing Attempts</span>
                  <span className="text-slate-200 font-mono text-sm font-semibold">{job.processingAttempts}</span>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Received At</span>
                  <span className="text-slate-300">{formatDate(job.receivedAt)}</span>
                </div>
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Started At</span>
                  <span className="text-slate-300">{formatDate(job.startedAt)}</span>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Completed At</span>
                  <span className="text-slate-300">{formatDate(job.completedAt)}</span>
                </div>
                <div>
                  <span className="text-slate-500 block text-[10px] uppercase font-semibold">Last Updated At</span>
                  <span className="text-slate-300">{formatDate(job.updatedAt)}</span>
                </div>
              </div>

              {/* UMIS Acknowledgement Card */}
              <div className="p-3 bg-slate-950/60 rounded-lg border border-slate-800/80 mt-2">
                <div className="flex items-center gap-2 text-[11px] font-semibold text-slate-300 mb-1">
                  <Send className="w-3.5 h-3.5 text-emerald-400" />
                  <span>UMIS Downstream Acknowledgement</span>
                </div>
                <div className="text-[11px] text-slate-400 flex items-center justify-between">
                  <span>Consumer: <strong className="text-slate-200">PO_PROCESSING</strong></span>
                  <span className="text-emerald-400 flex items-center gap-1">
                    <CheckCircle2 className="w-3 h-3" />
                    <span>Reported</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Attachments Section */}
        <div className="bg-slate-900/60 border border-slate-800 rounded-xl overflow-hidden">
          <div className="p-4 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center gap-2 text-white font-semibold text-sm">
              <Paperclip className="w-4 h-4 text-slate-400" />
              <span>Email Attachments ({job.attachments?.length || 0})</span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-800 bg-slate-950/40 text-[11px] text-slate-400 font-semibold uppercase tracking-wider">
                  <th className="py-2.5 px-4">Filename</th>
                  <th className="py-2.5 px-4">MIME Type</th>
                  <th className="py-2.5 px-4">Size</th>
                  <th className="py-2.5 px-4">Storage Retrieval</th>
                  <th className="py-2.5 px-4">File Hash (SHA-256)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60 text-slate-300">
                {!job.attachments || job.attachments.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-6 text-center text-slate-500">
                      No attachments were received with this email message.
                    </td>
                  </tr>
                ) : (
                  job.attachments.map((att) => (
                    <tr key={att.id} className="hover:bg-slate-800/30">
                      <td className="py-3 px-4 font-medium text-white flex items-center gap-2">
                        <FileText className="w-4 h-4 text-amber-400 shrink-0" />
                        <span>{att.originalFilename || 'Unnamed Attachment'}</span>
                      </td>
                      <td className="py-3 px-4 font-mono text-[11px] text-slate-400">
                        {att.contentType || 'application/octet-stream'}
                      </td>
                      <td className="py-3 px-4 whitespace-nowrap text-slate-300">
                        {formatFileSize(att.fileSize)}
                      </td>
                      <td className="py-3 px-4 whitespace-nowrap">
                        <PoStatusBadge status={att.processingStatus} />
                      </td>
                      <td className="py-3 px-4 font-mono text-[10px] text-slate-500 max-w-xs truncate" title={att.fileHash || ''}>
                        {att.fileHash ? (
                          <span className="flex items-center gap-1">
                            <Hash className="w-3 h-3 text-slate-600 shrink-0" />
                            <span className="truncate">{att.fileHash}</span>
                          </span>
                        ) : (
                          '—'
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </main>
  );
};
