import React from 'react';

interface StatusBadgeProps {
  status: string;
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const getBadgeStyle = (value: string) => {
    switch (value.toUpperCase()) {
      case 'ACTIVE':
      case 'CONNECTED':
      case 'SUCCESS':
      case 'PUBLISHED':
      case 'UP':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20';
      case 'SYNCHRONIZING':
      case 'FETCHING':
      case 'PROCESSING':
      case 'PENDING':
        return 'bg-sky-500/10 text-sky-400 border-sky-500/20';
      case 'REVIEW_REQUIRED':
      case 'DEGRADED':
      case 'AUTH_EXPIRED':
        return 'bg-amber-500/10 text-amber-400 border-amber-500/20';
      case 'FAILED':
      case 'CONNECTION_ERROR':
      case 'DOWN':
      case 'SUSPENDED':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/20';
      case 'DUPLICATE':
      case 'IGNORED':
      case 'DISCONNECTED':
      default:
        return 'bg-slate-500/10 text-slate-400 border-slate-500/20';
    }
  };

  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border ${getBadgeStyle(
        status
      )}`}
    >
      <span className="w-1.5 h-1.5 rounded-full mr-1.5 bg-current opacity-70" />
      {status.replace(/_/g, ' ')}
    </span>
  );
};
