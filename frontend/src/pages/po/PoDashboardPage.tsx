import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { poApi } from '../../api/poClient';
import { PoDashboardSummaryCards } from '../../components/po/PoDashboardSummaryCards';
import { PoJobsTable } from '../../components/po/PoJobsTable';
import { FileSpreadsheet, Activity, CheckCircle2 } from 'lucide-react';

export const PoDashboardPage: React.FC = () => {
  const { data: summary, isLoading: isSummaryLoading } = useQuery({
    queryKey: ['poSummary'],
    queryFn: () => poApi.getSummary(),
    refetchInterval: 10000,
  });

  const { data: health } = useQuery({
    queryKey: ['poHealth'],
    queryFn: () => poApi.getHealth(),
    refetchInterval: 30000,
  });

  return (
    <main className="flex-1 overflow-y-auto p-6">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Module Header */}
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400">
              <FileSpreadsheet className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-white tracking-tight leading-none">
                PO Processor Monitoring
              </h1>
              <p className="text-xs text-slate-400 mt-1">
                Real-time downstream Purchase Order processing & attachment extraction tracking
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {health?.status === 'UP' ? (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs font-medium">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                <span>PO Service Online (Port 8085)</span>
              </div>
            ) : (
              <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-slate-800 border border-slate-700 text-slate-400 text-xs font-medium">
                <Activity className="w-3.5 h-3.5 text-slate-500" />
                <span>Checking Service...</span>
              </div>
            )}
          </div>
        </div>

        {/* Summary Metric Cards */}
        <PoDashboardSummaryCards summary={summary} isLoading={isSummaryLoading} />

        {/* Processing Jobs Table */}
        <PoJobsTable />
      </div>
    </main>
  );
};
