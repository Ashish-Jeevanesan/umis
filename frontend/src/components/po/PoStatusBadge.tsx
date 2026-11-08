import React from 'react';
import { JobProcessingStatus } from '../../types/po';

interface StatusBadgeProps {
  status: JobProcessingStatus | string;
  size?: 'sm' | 'md';
}

export const PoStatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'sm' }) => {
  const getBadgeStyle = (currentStatus: string) => {
    switch (currentStatus) {
      case 'READY_FOR_EXTRACTION':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'PROCESSING':
      case 'EXTRACTION_IN_PROGRESS':
        return 'bg-blue-500/10 text-blue-400 border-blue-500/30';
      case 'COMPLETED':
        return 'bg-teal-500/10 text-teal-400 border-teal-500/30';
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
      case 'READY_FOR_EXTRACTION':
        return 'bg-emerald-400';
      case 'PROCESSING':
      case 'EXTRACTION_IN_PROGRESS':
        return 'bg-blue-400 animate-pulse';
      case 'COMPLETED':
        return 'bg-teal-400';
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
