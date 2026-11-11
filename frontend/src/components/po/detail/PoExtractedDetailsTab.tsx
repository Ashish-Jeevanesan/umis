import React, { useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { poApi } from '../../../api/poClient';
import { ProcessingJobFullDetails } from '../../../types/po';
import {
  Edit3,
  ShieldAlert,
  Building,
  Truck,
  CreditCard,
  MapPin,
  Check,
  X,
} from 'lucide-react';

interface ExtractedDetailsTabProps {
  details: ProcessingJobFullDetails;
  onRefresh: () => void;
}

export const PoExtractedDetailsTab: React.FC<ExtractedDetailsTabProps> = ({ details, onRefresh }) => {
  const { currentUser } = useAuth();
  const po = details.purchaseOrder;
  const isApproved = po?.isApproved || po?.workflowStatus === 'APPROVED';

  const userRole = currentUser?.role || 'PO_VIEWER';
  const canEdit = ['GLOBAL_ADMIN', 'TENANT_ADMIN', 'PO_ADMIN', 'PO_APPROVER', 'PO_VERIFIER', 'PO_REVIEWER'].includes(userRole);

  const [editingField, setEditingField] = useState<string | null>(null);
  const [fieldValue, setFieldValue] = useState('');
  const [editReason, setEditReason] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const startEdit = (field: string, currentVal: string | number | null | undefined) => {
    if (isApproved || !canEdit) return;
    setEditingField(field);
    setFieldValue(currentVal != null ? String(currentVal) : '');
    setEditReason('');
    setErrorMessage(null);
  };

  const handleSaveEdit = async () => {
    if (!editingField) return;
    setIsSubmitting(true);
    setErrorMessage(null);
    try {
      await poApi.updateField(details.job.id, editingField, fieldValue, editReason || 'Manual correction');
      setEditingField(null);
      onRefresh();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Failed to update field');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      {errorMessage && (
        <div className="p-3 bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs rounded-lg">
          {errorMessage}
        </div>
      )}

      {/* PO Header Fields */}
      <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
            <Building className="w-3.5 h-3.5 text-amber-400" />
            <span>Purchase Order Header</span>
          </h3>
          {isApproved && (
            <span className="text-[10px] text-slate-500 font-medium">Read-only (Approved)</span>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 text-xs">
          <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800 flex items-center justify-between">
            <div>
              <span className="text-slate-500 text-[10px] uppercase font-semibold">PO Number</span>
              <p className="font-semibold text-white mt-0.5">{po?.poNumber || '—'}</p>
            </div>
            {!isApproved && canEdit && (
              <button
                onClick={() => startEdit('poNumber', po?.poNumber)}
                className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-white"
              >
                <Edit3 className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800 flex items-center justify-between">
            <div>
              <span className="text-slate-500 text-[10px] uppercase font-semibold">Grand Total</span>
              <p className="font-semibold text-emerald-400 mt-0.5">
                {po?.grandTotal != null ? `${po.currency} ${Number(po.grandTotal).toFixed(2)}` : '—'}
              </p>
            </div>
            {!isApproved && canEdit && (
              <button
                onClick={() => startEdit('grandTotal', po?.grandTotal)}
                className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-white"
              >
                <Edit3 className="w-3.5 h-3.5" />
              </button>
            )}
          </div>

          <div className="p-2.5 rounded-lg bg-slate-950/60 border border-slate-800 flex items-center justify-between">
            <div>
              <span className="text-slate-500 text-[10px] uppercase font-semibold">Payment Terms</span>
              <p className="font-semibold text-slate-200 mt-0.5">{po?.paymentTerms || '—'}</p>
            </div>
            {!isApproved && canEdit && (
              <button
                onClick={() => startEdit('paymentTerms', po?.paymentTerms)}
                className="p-1 hover:bg-slate-800 rounded text-slate-400 hover:text-white"
              >
                <Edit3 className="w-3.5 h-3.5" />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* Edit Modal / Inline Drawer */}
      {editingField && (
        <div className="p-4 rounded-xl bg-slate-950 border border-amber-500/40 shadow-lg space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-amber-400">Edit Field: {editingField}</span>
            <button onClick={() => setEditingField(null)} className="text-slate-500 hover:text-white">
              <X className="w-4 h-4" />
            </button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <input
              type="text"
              value={fieldValue}
              onChange={(e) => setFieldValue(e.target.value)}
              placeholder="New field value"
              className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white"
            />
            <input
              type="text"
              value={editReason}
              onChange={(e) => setEditReason(e.target.value)}
              placeholder="Reason for change (audit trail)"
              className="bg-slate-900 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-white"
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              onClick={() => setEditingField(null)}
              className="px-3 py-1 rounded text-xs text-slate-400 hover:text-white"
            >
              Cancel
            </button>
            <button
              onClick={handleSaveEdit}
              disabled={isSubmitting}
              className="px-3 py-1 rounded text-xs font-semibold bg-amber-500 text-slate-950 hover:bg-amber-400 flex items-center gap-1"
            >
              <Check className="w-3.5 h-3.5" />
              <span>Save & Audit</span>
            </button>
          </div>
        </div>
      )}

      {/* Vendor & Entity Sections */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
        {/* Vendor Information */}
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
          <h4 className="font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
            <Truck className="w-3.5 h-3.5 text-amber-400" />
            <span>Vendor / Supplier</span>
          </h4>
          <p><span className="text-slate-500">Name:</span> <span className="text-slate-200 font-medium">{po?.vendorName || '—'}</span></p>
          <p><span className="text-slate-500">Contact:</span> <span className="text-slate-200">{po?.vendorContact || '—'}</span></p>
          <p><span className="text-slate-500">Email:</span> <span className="text-slate-200">{po?.vendorEmail || '—'}</span></p>
          <p><span className="text-slate-500">Tax ID:</span> <span className="text-slate-200">{po?.vendorTaxId || '—'}</span></p>
          <p><span className="text-slate-500">Address:</span> <span className="text-slate-200">{po?.vendorAddress || '—'}</span></p>
        </div>

        {/* Patient Reference (Sensitive) */}
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
              <ShieldAlert className="w-3.5 h-3.5 text-rose-400" />
              <span>Patient Reference</span>
            </h4>
            <span className="text-[10px] text-rose-400/80 bg-rose-500/10 px-2 py-0.5 rounded border border-rose-500/20">
              Restricted Clinical Data
            </span>
          </div>
          <p><span className="text-slate-500">Patient Name:</span> <span className="text-slate-200 font-medium">{details.patientDetail?.patientName || '—'}</span></p>
          <p><span className="text-slate-500">MRN / ID:</span> <span className="text-slate-200">{details.patientDetail?.patientIdMrn || '—'}</span></p>
          <p><span className="text-slate-500">Surgeon / Doctor:</span> <span className="text-slate-200">{details.patientDetail?.surgeonDoctorName || '—'}</span></p>
          <p><span className="text-slate-500">Procedure Ref:</span> <span className="text-slate-200">{details.patientDetail?.procedureCaseReference || '—'}</span></p>
        </div>

        {/* Billing Information */}
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
          <h4 className="font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
            <CreditCard className="w-3.5 h-3.5 text-sky-400" />
            <span>Billing Information</span>
          </h4>
          <p><span className="text-slate-500">Entity:</span> <span className="text-slate-200">{details.billingDetail?.billingEntity || '—'}</span></p>
          <p><span className="text-slate-500">Address:</span> <span className="text-slate-200">{details.billingDetail?.billingAddress || '—'}</span></p>
          <p><span className="text-slate-500">Tax ID:</span> <span className="text-slate-200">{details.billingDetail?.taxId || '—'}</span></p>
        </div>

        {/* Shipping Information */}
        <div className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2">
          <h4 className="font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
            <MapPin className="w-3.5 h-3.5 text-emerald-400" />
            <span>Shipping Destination</span>
          </h4>
          <p><span className="text-slate-500">Ship To:</span> <span className="text-slate-200">{details.shippingDetail?.shipToEntity || '—'}</span></p>
          <p><span className="text-slate-500">Address:</span> <span className="text-slate-200">{details.shippingDetail?.shippingAddress || '—'}</span></p>
          <p><span className="text-slate-500">Instructions:</span> <span className="text-slate-200">{details.shippingDetail?.deliveryInstructions || '—'}</span></p>
        </div>
      </div>
    </div>
  );
};
