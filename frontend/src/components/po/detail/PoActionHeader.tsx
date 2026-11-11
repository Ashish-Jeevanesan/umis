import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../../context/AuthContext';
import { poApi } from '../../../api/poClient';
import { ProcessingJobFullDetails } from '../../../types/po';
import { PoStatusBadge } from '../PoStatusBadge';
import {
  ArrowLeft,
  CheckCircle,
  Award,
  RefreshCw,
  AlertTriangle,
} from 'lucide-react';

interface ActionHeaderProps {
  details: ProcessingJobFullDetails;
  onRefresh: () => void;
}

export const PoActionHeader: React.FC<ActionHeaderProps> = ({ details, onRefresh }) => {
  const navigate = useNavigate();
  const { currentUser } = useAuth();
  const [isActionLoading, setIsActionLoading] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const po = details.purchaseOrder;
  const isApproved = po?.isApproved || po?.workflowStatus === 'APPROVED';
  const isVerified = po?.workflowStatus === 'VERIFIED';
  const isDuplicate = details.job.processingStatus === 'DUPLICATE';

  const userRole = currentUser?.role || 'PO_VIEWER';
  const canVerify = ['GLOBAL_ADMIN', 'TENANT_ADMIN', 'PO_ADMIN', 'PO_APPROVER', 'PO_VERIFIER'].includes(userRole);
  const canApprove = ['GLOBAL_ADMIN', 'TENANT_ADMIN', 'PO_ADMIN', 'PO_APPROVER'].includes(userRole);
  const canReExtract = ['GLOBAL_ADMIN', 'TENANT_ADMIN', 'PO_ADMIN'].includes(userRole);

  const handleVerify = async () => {
    setIsActionLoading(true);
    setActionError(null);
    try {
      await poApi.verifyJob(details.job.id, 'Verified via review dashboard');
      onRefresh();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Verification failed');
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleApprove = async () => {
    if (!window.confirm('Are you sure you want to approve this Purchase Order? It will become read-only.')) {
      return;
    }
    setIsActionLoading(true);
    setActionError(null);
    try {
      await poApi.approveJob(details.job.id, 'Approved via review dashboard');
      onRefresh();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Approval failed');
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleReExtract = async () => {
    setIsActionLoading(true);
    setActionError(null);
    try {
      await poApi.reExtractJob(details.job.id);
      onRefresh();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Re-extraction trigger failed');
    } finally {
      setIsActionLoading(false);
    }
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate('/po')}
            className="p-2 rounded-lg bg-slate-900 border border-slate-800 hover:bg-slate-800 text-slate-300 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <div className="flex items-center gap-2.5">
              <h1 className="text-xl font-bold text-white tracking-tight">
                {po?.poNumber ? `Purchase Order: ${po.poNumber}` : 'PO Processing Review'}
              </h1>
              <PoStatusBadge status={details.job.processingStatus} />
              {po?.workflowStatus && <PoStatusBadge status={po.workflowStatus} size="sm" />}
            </div>
            <p className="text-xs text-slate-400 mt-0.5">
              Job ID: <span className="font-mono text-slate-300">{details.job.id}</span> • Role:{' '}
              <span className="font-semibold text-amber-400">{userRole}</span>
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2.5">
          <button
            onClick={handleReExtract}
            disabled={isActionLoading || isApproved || !canReExtract}
            title={!canReExtract ? 'Requires PO_ADMIN role' : undefined}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-300 bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-colors disabled:opacity-40"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isActionLoading ? 'animate-spin' : ''}`} />
            <span>Re-extract</span>
          </button>

          <button
            onClick={handleVerify}
            disabled={isActionLoading || isApproved || isDuplicate || !canVerify}
            title={!canVerify ? 'Requires PO_VERIFIER role' : undefined}
            className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold text-cyan-300 bg-cyan-500/10 hover:bg-cyan-500/20 border border-cyan-500/30 transition-colors disabled:opacity-40"
          >
            <CheckCircle className="w-4 h-4" />
            <span>Verify</span>
          </button>

          <button
            onClick={handleApprove}
            disabled={isActionLoading || !isVerified || isApproved || isDuplicate || !canApprove}
            title={!canApprove ? 'Requires PO_APPROVER role' : undefined}
            className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold text-emerald-950 bg-emerald-400 hover:bg-emerald-300 transition-colors disabled:opacity-40 shadow-sm"
          >
            <Award className="w-4 h-4" />
            <span>{isApproved ? 'Approved' : 'Approve PO'}</span>
          </button>
        </div>
      </div>

      {actionError && (
        <div className="p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-300 text-xs flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
          <span>{actionError}</span>
        </div>
      )}
    </div>
  );
};
