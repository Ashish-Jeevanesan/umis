import React from 'react';
import { ProcessingJobFullDetails, FieldComparisonItem } from '../../../types/po';
import {
  GitCompare,
  CheckCircle,
  AlertTriangle,
  FileCheck,
} from 'lucide-react';

interface ComparisonTabProps {
  details: ProcessingJobFullDetails;
}

export const PoComparisonTab: React.FC<ComparisonTabProps> = ({ details }) => {
  const comparison = details.comparison;
  let items: FieldComparisonItem[] = [];

  if (comparison?.comparisonReport) {
    try {
      items = JSON.parse(comparison.comparisonReport);
    } catch {
      items = [];
    }
  }

  const getResultBadge = (result: string) => {
    switch (result) {
      case 'MATCH':
        return 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30';
      case 'MISMATCH':
        return 'bg-rose-500/10 text-rose-400 border-rose-500/30';
      default:
        return 'bg-slate-700/30 text-slate-400 border-slate-700/50';
    }
  };

  return (
    <div className="space-y-4">
      {/* Status Card */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          <GitCompare className="w-4 h-4 text-amber-400" />
          <div>
            <h4 className="text-xs font-semibold text-white uppercase tracking-wider">
              Comparison Classification: {comparison?.comparisonStatus || 'ATTACHMENT_ONLY_RELEVANT'}
            </h4>
            <p className="text-[11px] text-slate-400 mt-0.5">
              Reconciliation between incoming email body text and attachment extraction
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {comparison?.conflictCount ? (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-rose-500/10 text-rose-400 border border-rose-500/30">
              <AlertTriangle className="w-3.5 h-3.5" />
              <span>{comparison.conflictCount} Conflict(s) Detected</span>
            </span>
          ) : (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
              <CheckCircle className="w-3.5 h-3.5" />
              <span>Zero Conflicts</span>
            </span>
          )}
        </div>
      </div>

      {/* Comparison Table */}
      <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] text-slate-400 font-semibold uppercase tracking-wider">
              <th className="py-2.5 px-4">Field</th>
              <th className="py-2.5 px-4">Email Body Value</th>
              <th className="py-2.5 px-4">Attachment Value</th>
              <th className="py-2.5 px-4">Result</th>
              <th className="py-2.5 px-4">Comments</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {items.length === 0 ? (
              <tr>
                <td colSpan={5} className="py-8 text-center text-slate-500">
                  <FileCheck className="w-6 h-6 mx-auto mb-1 text-slate-600" />
                  <p>No comparison fields available for this document.</p>
                </td>
              </tr>
            ) : (
              items.map((item, idx) => (
                <tr key={idx} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-2.5 px-4 font-semibold text-white">{item.fieldName}</td>
                  <td className="py-2.5 px-4 font-mono text-slate-300">{item.emailValue || '—'}</td>
                  <td className="py-2.5 px-4 font-mono text-slate-300">{item.attachmentValue || '—'}</td>
                  <td className="py-2.5 px-4">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold border ${getResultBadge(item.matchResult)}`}>
                      {item.matchResult}
                    </span>
                  </td>
                  <td className="py-2.5 px-4 text-slate-400 text-[11px]">{item.comments || '—'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
