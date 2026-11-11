import React from 'react';
import { PoDashboardSummary } from '../../types/po';
import {
  FileSpreadsheet,
  FileCheck2,
  Clock,
  AlertTriangle,
  CheckCheck,
  Award,
  Copy,
  AlertOctagon,
} from 'lucide-react';

interface SummaryCardsProps {
  summary: PoDashboardSummary | undefined;
  isLoading: boolean;
}

export const PoDashboardSummaryCards: React.FC<SummaryCardsProps> = ({ summary, isLoading }) => {
  const cards = [
    {
      title: 'Total Received',
      value: summary?.totalReceived ?? 0,
      icon: FileSpreadsheet,
      color: 'text-sky-400',
      bgColor: 'bg-sky-500/10 border-sky-500/20',
    },
    {
      title: 'POs Identified',
      value: summary?.poIdentified ?? summary?.completed ?? 0,
      icon: FileCheck2,
      color: 'text-teal-400',
      bgColor: 'bg-teal-500/10 border-teal-500/20',
    },
    {
      title: 'Ready for Review',
      value: summary?.readyForReview ?? summary?.readyForExtraction ?? 0,
      icon: Clock,
      color: 'text-emerald-400',
      bgColor: 'bg-emerald-500/10 border-emerald-500/20',
    },
    {
      title: 'Needs Review',
      value: summary?.needsReview ?? 0,
      icon: AlertTriangle,
      color: 'text-orange-400',
      bgColor: 'bg-orange-500/10 border-orange-500/20',
    },
    {
      title: 'Verified',
      value: summary?.verified ?? 0,
      icon: CheckCheck,
      color: 'text-cyan-400',
      bgColor: 'bg-cyan-500/10 border-cyan-500/20',
    },
    {
      title: 'Approved',
      value: summary?.approved ?? 0,
      icon: Award,
      color: 'text-emerald-400',
      bgColor: 'bg-emerald-500/10 border-emerald-500/20',
    },
    {
      title: 'Duplicates',
      value: summary?.duplicate ?? 0,
      icon: Copy,
      color: 'text-violet-400',
      bgColor: 'bg-violet-500/10 border-violet-500/20',
    },
    {
      title: 'Failed',
      value: summary?.failed ?? 0,
      icon: AlertOctagon,
      color: 'text-rose-400',
      bgColor: 'bg-rose-500/10 border-rose-500/20',
    },
  ];

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
      {cards.map((card) => {
        const Icon = card.icon;
        return (
          <div
            key={card.title}
            className={`p-3.5 rounded-xl border bg-slate-900/60 transition-all ${card.bgColor}`}
          >
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider truncate">
                {card.title}
              </span>
              <Icon className={`w-3.5 h-3.5 shrink-0 ${card.color}`} />
            </div>
            <div className="text-xl font-bold text-white tracking-tight">
              {isLoading ? (
                <div className="h-6 w-10 bg-slate-800 animate-pulse rounded" />
              ) : (
                card.value.toLocaleString()
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
};
