import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { poApi } from '../../../api/poClient';
import { ProcessingJobFullDetails } from '../../../types/po';
import { Building2, DollarSign, Calendar, FileCheck } from 'lucide-react';

interface HospitalHistoryTabProps {
  details: ProcessingJobFullDetails;
}

export const PoHospitalHistoryTab: React.FC<HospitalHistoryTabProps> = ({ details }) => {
  const hospitalId = details.purchaseOrder?.hospital?.id || details.purchaseOrder?.hospitalId;
  const tenantId = details.job.tenantId;

  const { data: history, isLoading } = useQuery({
    queryKey: ['hospitalHistory', hospitalId, tenantId],
    queryFn: () => (hospitalId ? poApi.getHospitalHistory(hospitalId, tenantId) : Promise.reject('No hospital ID')),
    enabled: !!hospitalId,
  });

  if (!hospitalId) {
    return (
      <div className="p-8 text-center text-slate-500 bg-slate-900/40 rounded-xl border border-slate-800">
        <Building2 className="w-8 h-8 mx-auto mb-2 text-slate-600" />
        <p>No hospital resolved for this purchase order yet.</p>
      </div>
    );
  }

  if (isLoading) {
    return (
      <div className="p-8 text-center text-slate-400">
        <div className="h-6 w-32 bg-slate-800 rounded mx-auto animate-pulse" />
      </div>
    );
  }

  const currencyTotals = history?.approvedTotalsByCurrency || {};

  return (
    <div className="space-y-6">
      {/* Metrics Row */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <FileCheck className="w-3.5 h-3.5 text-teal-400" />
            <span>Total Approved POs</span>
          </div>
          <div className="text-2xl font-bold text-white tracking-tight">
            {history?.totalApprovedPoCount ?? 0}
          </div>
          <p className="text-[11px] text-slate-500 mt-1">Non-duplicate approved submissions</p>
        </div>

        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <DollarSign className="w-3.5 h-3.5 text-emerald-400" />
            <span>Lifetime Approved Totals</span>
          </div>
          <div className="space-y-1 mt-1">
            {Object.keys(currencyTotals).length === 0 ? (
              <span className="text-slate-500 text-xs">No approved totals yet</span>
            ) : (
              Object.entries(currencyTotals).map(([curr, amt]) => (
                <div key={curr} className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-slate-300">{curr}:</span>
                  <span className="font-mono font-bold text-emerald-400">
                    {Number(amt).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <Calendar className="w-3.5 h-3.5 text-purple-400" />
            <span>Activity Window</span>
          </div>
          <div className="text-xs text-slate-300 space-y-1 mt-1">
            <p>First PO: {history?.firstPoReceivedDate ? new Date(history.firstPoReceivedDate).toLocaleDateString() : '—'}</p>
            <p>Latest PO: {history?.latestPoReceivedDate ? new Date(history.latestPoReceivedDate).toLocaleDateString() : '—'}</p>
          </div>
        </div>
      </div>

      {/* Historical PO Table */}
      <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
        <div className="p-3 border-b border-slate-800 bg-slate-950/60 font-semibold text-xs text-white">
          Historical Purchase Orders for {history?.hospitalName || 'Hospital'}
        </div>
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-slate-800 bg-slate-950/40 text-[11px] text-slate-400 font-semibold uppercase">
              <th className="py-2.5 px-4">PO Number</th>
              <th className="py-2.5 px-4">Date</th>
              <th className="py-2.5 px-4">Vendor</th>
              <th className="py-2.5 px-4 text-right">Amount</th>
              <th className="py-2.5 px-4">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/60 text-slate-300">
            {(!history?.historicalPurchaseOrders || history.historicalPurchaseOrders.length === 0) ? (
              <tr>
                <td colSpan={5} className="py-6 text-center text-slate-500">No previous orders recorded for this hospital.</td>
              </tr>
            ) : (
              history.historicalPurchaseOrders.map((hPo) => (
                <tr key={hPo.poId} className="hover:bg-slate-800/30 transition-colors">
                  <td className="py-2.5 px-4 font-mono font-medium text-white">{hPo.poNumber}</td>
                  <td className="py-2.5 px-4 text-slate-400">{hPo.poDate || '—'}</td>
                  <td className="py-2.5 px-4 text-slate-300">{hPo.vendorName || '—'}</td>
                  <td className="py-2.5 px-4 text-right font-mono font-semibold text-emerald-400">
                    {hPo.currency} {Number(hPo.grandTotal).toFixed(2)}
                  </td>
                  <td className="py-2.5 px-4">
                    <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                      hPo.isApproved ? 'bg-emerald-500/10 text-emerald-400' : 'bg-slate-700/30 text-slate-400'
                    }`}>
                      {hPo.isApproved ? 'APPROVED' : 'PENDING'}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};
