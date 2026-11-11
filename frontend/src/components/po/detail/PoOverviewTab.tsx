import React from 'react';
import { ProcessingJobFullDetails } from '../../../types/po';
import {
  DollarSign,
  Building2,
  Calendar,
  Truck,
  Copy,
  AlertTriangle,
} from 'lucide-react';

interface OverviewTabProps {
  details: ProcessingJobFullDetails;
}

export const PoOverviewTab: React.FC<OverviewTabProps> = ({ details }) => {
  const po = details.purchaseOrder;
  const job = details.job;
  const isDuplicate = details.duplicateDetections && details.duplicateDetections.length > 0;
  const dupInfo = isDuplicate ? details.duplicateDetections[0] : null;

  return (
    <div className="space-y-6">
      {/* Duplicate Alert Banner */}
      {isDuplicate && (
        <div className="p-4 rounded-xl bg-violet-500/10 border border-violet-500/30 text-violet-200">
          <div className="flex items-center gap-2 font-semibold text-sm text-violet-300">
            <Copy className="w-4 h-4 text-violet-400" />
            <span>Duplicate Submission Detected ({dupInfo?.duplicateType})</span>
          </div>
          <p className="text-xs text-violet-300/80 mt-1">{dupInfo?.detectionReason}</p>
        </div>
      )}

      {/* Financial & Entity Highlights */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <DollarSign className="w-3.5 h-3.5 text-emerald-400" />
            <span>Grand Total</span>
          </div>
          <div className="text-2xl font-bold text-white tracking-tight">
            {po?.grandTotal != null
              ? `${po.currency || 'USD'} ${Number(po.grandTotal).toLocaleString(undefined, {
                  minimumFractionDigits: 2,
                })}`
              : '—'}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Subtotal: {po?.subtotal ? `${po.currency} ${Number(po.subtotal).toFixed(2)}` : '—'} • Tax:{' '}
            {po?.taxAmount ? `${po.currency} ${Number(po.taxAmount).toFixed(2)}` : '0.00'}
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <Building2 className="w-3.5 h-3.5 text-sky-400" />
            <span>Hospital / Customer</span>
          </div>
          <div className="text-base font-bold text-white truncate">
            {po?.hospital?.legalName || po?.hospital?.normalizedName || po?.hospitalName || po?.hospitalLocationName || 'Unassigned Hospital'}
          </div>
          <div className="text-[11px] text-slate-400 mt-1 truncate">
            Code: {po?.hospital?.hospitalCode || po?.hospitalCode || '—'}
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <Truck className="w-3.5 h-3.5 text-amber-400" />
            <span>Vendor / Supplier</span>
          </div>
          <div className="text-base font-bold text-white truncate">
            {po?.vendorName || 'Unassigned Vendor'}
          </div>
          <div className="text-[11px] text-slate-400 mt-1 truncate">
            Contact: {po?.vendorContact || po?.vendorEmail || '—'}
          </div>
        </div>

        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
            <Calendar className="w-3.5 h-3.5 text-purple-400" />
            <span>PO & Delivery Dates</span>
          </div>
          <div className="text-sm font-bold text-white">
            Date: {po?.poDate ? new Date(po.poDate).toLocaleDateString() : '—'}
          </div>
          <div className="text-[11px] text-slate-400 mt-1">
            Delivery: {po?.requestedDeliveryDate ? new Date(po.requestedDeliveryDate).toLocaleDateString() : '—'}
          </div>
        </div>
      </div>

      {/* Processing Telemetry */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-3">
        <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider">Processing Telemetry</h3>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
          <div>
            <span className="text-slate-400">Received At:</span>
            <p className="font-medium text-slate-200 mt-0.5">{new Date(job.receivedAt).toLocaleString()}</p>
          </div>
          <div>
            <span className="text-slate-400">Extraction Method:</span>
            <p className="font-medium text-slate-200 mt-0.5">
              {details.documentAnalyses?.[0]?.extractionMethod || 'DIGITAL_TEXT_EXTRACTION'}
            </p>
          </div>
          <div>
            <span className="text-slate-400">OCR Executed:</span>
            <p className="font-medium text-slate-200 mt-0.5">
              {details.documentAnalyses?.[0]?.ocrUsed ? 'Yes (Vision/OCR)' : 'No (Digital Text)'}
            </p>
          </div>
          <div>
            <span className="text-slate-400">Total Attachments:</span>
            <p className="font-medium text-slate-200 mt-0.5">{job.attachments?.length ?? 0}</p>
          </div>
        </div>

        {job.lastError && (
          <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-2 mt-2">
            <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
            <span className="font-mono">{job.lastError}</span>
          </div>
        )}
      </div>
    </div>
  );
};
