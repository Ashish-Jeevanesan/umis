import React from 'react';
import { ProcessingJobFullDetails, ExtractedPoLineItem } from '../../../types/po';
import { Layers, FileText } from 'lucide-react';

interface LineItemsTabProps {
  details: ProcessingJobFullDetails;
}

export const PoLineItemsTab: React.FC<LineItemsTabProps> = ({ details }) => {
  const lineItems = details.lineItems || [];
  const po = details.purchaseOrder;

  const totalCalculated = lineItems.reduce(
    (sum, item) => sum + (Number(item.lineTotal) || 0),
    0
  );

  return (
    <div className="space-y-4">
      {/* Header Metric */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-3.5 rounded-xl bg-slate-900/60 border border-slate-800 text-xs">
        <div className="flex items-center gap-2">
          <Layers className="w-4 h-4 text-amber-400" />
          <span className="font-semibold text-white">Extracted Line Items: {lineItems.length}</span>
        </div>
        <div className="text-slate-400">
          Calculated Line Items Sum:{' '}
          <span className="font-semibold text-emerald-400 font-mono">
            {po?.currency || 'USD'} {totalCalculated.toLocaleString(undefined, { minimumFractionDigits: 2 })}
          </span>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] text-slate-400 font-semibold uppercase tracking-wider">
                <th className="py-2.5 px-3">#</th>
                <th className="py-2.5 px-3">Code / SKU</th>
                <th className="py-2.5 px-3">Product Name & Description</th>
                <th className="py-2.5 px-3">Category</th>
                <th className="py-2.5 px-3 text-right">Qty</th>
                <th className="py-2.5 px-3">Unit</th>
                <th className="py-2.5 px-3 text-right">Unit Price</th>
                <th className="py-2.5 px-3 text-right">Tax (%)</th>
                <th className="py-2.5 px-3 text-right">Line Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {lineItems.length === 0 ? (
                <tr>
                  <td colSpan={9} className="py-8 text-center text-slate-500">
                    <FileText className="w-6 h-6 mx-auto mb-1 text-slate-600" />
                    <p>No individual line items extracted from document.</p>
                  </td>
                </tr>
              ) : (
                lineItems.map((item: ExtractedPoLineItem) => (
                  <tr key={item.id || item.lineNumber} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-2.5 px-3 font-mono text-slate-500">{item.lineNumber}</td>
                    <td className="py-2.5 px-3 font-mono font-medium text-white">{item.productCode || '—'}</td>
                    <td className="py-2.5 px-3 max-w-xs">
                      <p className="font-medium text-slate-200">{item.productName || '—'}</p>
                      {item.description && (
                        <p className="text-[11px] text-slate-500 truncate mt-0.5">{item.description}</p>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-slate-400">{item.category || '—'}</td>
                    <td className="py-2.5 px-3 text-right font-mono font-medium text-slate-200">
                      {Number(item.quantity).toLocaleString()}
                    </td>
                    <td className="py-2.5 px-3 text-slate-400">{item.unit || 'EA'}</td>
                    <td className="py-2.5 px-3 text-right font-mono text-slate-300">
                      {Number(item.unitPrice).toFixed(2)}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono text-slate-400">
                      {item.taxPercentage != null ? `${Number(item.taxPercentage).toFixed(1)}%` : '—'}
                    </td>
                    <td className="py-2.5 px-3 text-right font-mono font-semibold text-emerald-400">
                      {Number(item.lineTotal).toFixed(2)}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
