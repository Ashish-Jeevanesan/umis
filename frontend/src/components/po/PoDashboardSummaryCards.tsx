import React from 'react';
import { PoDashboardSummary } from '../../types/po';
import {
  FileSpreadsheet,
  Clock,
  RefreshCw,
  FileCheck2,
  CheckCircle,
  AlertOctagon,
} from 'lucide-react';

interface SummaryCardsProps {
  summary: PoDashboardSummary | undefined;
  isLoading: boolean;
}

export const PoDashboardSummaryCards: React.FC<SummaryCardsProps> = ({ summary, isLoading }) => {
  const cards = [
    {
      title: 'Total Emails Received',
      value: summary?.totalReceived ?? 0,
      icon: FileSpreadsheet,
      color: 'text-sky-400',
      bgColor: 'bg-sky-500/10 border-sky-500/20',
    },
    {
      title: 'Pending Processing',
      value: summary?.pending ?? 0,
      icon: Clock,
      color: 'text-amber-400',
      bgColor: 'bg-amber-500/10 border-amber-500/20',
    },
    {
      title: 'Currently Processing',
      value: summary?.processing ?? 0,
      icon: RefreshCw,
      color: 'text-blue-400',
      bgColor: 'bg-blue-500/10 border-blue-500/20',
    },
    {
      title: 'Ready for Extraction',
      value: summary?.readyForExtraction ?? 0,
      icon: FileCheck2,
      color: 'text-emerald-400',
      bgColor: 'bg-emerald-500/10 border-emerald-500/20',
    },
    {
      title: 'Successfully Completed',
      value: summary?.completed ?? 0,
      icon: CheckCircle,
      color: 'text-teal-400',
      bgColor: 'bg-teal-500/10 border-teal-500/20',
    },
    {
      title: 'Failed Processing Jobs',
      value: summary?.failed ?? 0,
      icon: AlertOctagon,
      color: 'text-rose-400',
      bgColor: 'bg-rose-500/10 border-rose-500/20',
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
      {cards.map((card) => {
        const Icon = card.icon;
        return (
          <div
            key={card.title}
            className={`p-4 rounded-xl border bg-slate-900/60 transition-all ${card.bgColor}`}
          >
            <div className="flex items-center justify-between mb-2">
              <span className="text-[11px] font-medium text-slate-400 uppercase tracking-wider">
                {card.title}
              </span>
              <Icon className={`w-4 h-4 ${card.color}`} />
            </div>
            <div className="text-2xl font-bold text-white tracking-tight">
              {isLoading ? (
                <div className="h-7 w-12 bg-slate-800 animate-pulse rounded" />
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
