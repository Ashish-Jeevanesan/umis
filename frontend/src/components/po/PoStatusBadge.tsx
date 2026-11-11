import React from 'react';
import { JobProcessingStatus, PoWorkflowStatus } from '../../types/po';

interface StatusBadgeProps {
  status: JobProcessingStatus | PoWorkflowStatus | string;
  size?: 'sm' | 'md';
}

export const PoStatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'sm' }) => {
  const getBadgeStyle = (currentStatus: string) => {
    switch (currentStatus) {
      case 'APPROVED':
      case 'COMPLETED':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'VERIFIED':
        return 'bg-cyan-500/10 text-cyan-400 border-cyan-500/30';
      case 'READY_FOR_REVIEW':
      case 'READY_FOR_EXTRACTION':
        return 'bg-teal-500/10 text-teal-400 border-teal-500/30';
      case 'PROCESSING':
      case 'EXTRACTION_IN_PROGRESS':
        return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
      case 'QUEUED':
        return 'bg-purple-500/10 text-purple-400 border-purple-500/30';
      case 'NEEDS_REVIEW':
        return 'bg-orange-500/10 text-orange-400 border-orange-500/30';
      case 'EDITED':
        return 'bg-yellow-500/10 text-yellow-400 border-yellow-500/30';
      case 'DUPLICATE':
        return 'bg-violet-500/10 text-violet-400 border-violet-500/30';
      case 'PENDING':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/30';
      case 'FAILED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      default:
        return 'bg-slate-700/30 text-slate-400 border-slate-700/50';
    }
  };

  const getDotStyle = (currentStatus: string) => {
    switch (currentStatus) {
      case 'APPROVED':
      case 'COMPLETED':
        return 'bg-emerald-400';
      case 'VERIFIED':
        return 'bg-cyan-400';
      case 'READY_FOR_REVIEW':
      case 'READY_FOR_EXTRACTION':
        return 'bg-teal-400';
      case 'PROCESSING':
      case 'EXTRACTION_IN_PROGRESS':
        return 'bg-blue-400 animate-pulse';
      case 'QUEUED':
        return 'bg-purple-400 animate-pulse';
      case 'NEEDS_REVIEW':
        return 'bg-orange-400';
      case 'EDITED':
        return 'bg-yellow-400';
      case 'DUPLICATE':
        return 'bg-violet-400';
      case 'PENDING':
        return 'bg-amber-400';
      case 'FAILED':
        return 'bg-rose-400';
      default:
        return 'bg-slate-400';
    }
  };

  const formatStatus = (rawStatus: string) => {
    return rawStatus.replace(/_/g, ' ');
  };

  const sizeClass = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-3 py-1 text-xs';

  return (
    <span
      className={`inline-flex items-center gap-1.5 font-medium rounded-full border ${getBadgeStyle(
        status
      )} ${sizeClass}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${getDotStyle(status)}`} />
      <span className="capitalize">{formatStatus(status).toLowerCase()}</span>
    </span>
  );
};
