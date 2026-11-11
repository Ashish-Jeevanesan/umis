import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { poApi } from '../../api/poClient';
import { PoActionHeader } from '../../components/po/detail/PoActionHeader';
import { PoOverviewTab } from '../../components/po/detail/PoOverviewTab';
import { PoExtractedDetailsTab } from '../../components/po/detail/PoExtractedDetailsTab';
import { PoLineItemsTab } from '../../components/po/detail/PoLineItemsTab';
import { PoComparisonTab } from '../../components/po/detail/PoComparisonTab';
import { PoHospitalHistoryTab } from '../../components/po/detail/PoHospitalHistoryTab';
import { PoDocumentsTab } from '../../components/po/detail/PoDocumentsTab';
import { PoValidationAuditTab } from '../../components/po/detail/PoValidationAuditTab';
import {
  FileSpreadsheet,
  FileText,
  Layers,
  GitCompare,
  Building2,
  Cpu,
  ShieldCheck,
  AlertCircle,
  ArrowLeft,
  RefreshCw,
  Lock,
} from 'lucide-react';

type TabKey =
  | 'overview'
  | 'extracted'
  | 'line_items'
  | 'comparison'
  | 'hospital_history'
  | 'documents'
  | 'validation_audit';

export const PoJobDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<TabKey>('overview');

  const { data: progressData } = useQuery({
    queryKey: ['poJobProgress', id],
    queryFn: () => poApi.getProgress(id!),
    enabled: Boolean(id),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return (status === 'PROCESSING' || status === 'QUEUED' || status === 'PENDING' || status === 'READY_FOR_EXTRACTION') ? 1500 : false;
    },
  });

  const isProcessing =
    progressData?.status === 'PROCESSING' ||
    progressData?.status === 'QUEUED' ||
    progressData?.status === 'PENDING' ||
    progressData?.status === 'READY_FOR_EXTRACTION';

  const {
    data: details,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ['poJobFullDetails', id],
    queryFn: () => poApi.getJobFullDetails(id!),
    enabled: Boolean(id) && !isProcessing,
  });

  useEffect(() => {
    if (progressData && !isProcessing && progressData.status !== 'FAILED') {
      refetch();
    }
  }, [progressData?.status, isProcessing, refetch]);

  if (isProcessing) {
    return (
      <main className="flex-1 p-6 overflow-y-auto bg-slate-950">
        <div className="max-w-4xl mx-auto space-y-6 pt-12">
          <button
            onClick={() => navigate('/po')}
            className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to PO Dashboard</span>
          </button>

          <div className="p-8 rounded-2xl bg-slate-900/90 border border-slate-800 shadow-xl space-y-6">
            <div className="flex items-center justify-between border-b border-slate-800 pb-5">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-sky-500/10 border border-sky-500/20 flex items-center justify-center text-sky-400">
                  <Lock className="w-5 h-5" />
                </div>
                <div>
                  <h2 className="text-lg font-bold text-white">PO Review Locked</h2>
                  <p className="text-xs text-slate-400 mt-0.5">
                    Document extraction and intelligence analysis underway
                  </p>
                </div>
              </div>
              <div className="inline-flex items-center gap-2 px-3 py-1 text-xs font-semibold text-sky-400 bg-sky-500/10 border border-sky-500/30 rounded-lg">
                <RefreshCw className="w-3.5 h-3.5 animate-spin text-sky-400" />
                <span>{progressData?.progressPercentage || 0}% Complete</span>
              </div>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between text-xs text-slate-300">
                <span className="font-semibold uppercase tracking-wider text-slate-400">
                  Current Stage: <span className="text-sky-300">{progressData?.stage || 'INITIALIZING'}</span>
                </span>
                <span className="font-mono text-slate-400">{progressData?.progressPercentage || 0}%</span>
              </div>
              <div className="w-full bg-slate-800 rounded-full h-3 overflow-hidden p-0.5">
                <div
                  className="bg-gradient-to-r from-sky-500 to-indigo-500 h-2 rounded-full transition-all duration-500"
                  style={{ width: `${progressData?.progressPercentage || 10}%` }}
                />
              </div>
              <p className="text-xs text-slate-400 italic">
                {progressData?.message || 'Processing incoming purchase order documents...'}
              </p>
            </div>

            <div className="p-4 rounded-xl bg-slate-950/60 border border-slate-800/80 text-xs text-slate-400 space-y-1">
              <p className="font-semibold text-slate-300">Why is this page locked?</p>
              <p>
                To maintain financial accuracy and regulatory compliance, the purchase order review screen is restricted
                until all OCR, AI understanding, line-item parsing, and balance reconciliation steps complete.
              </p>
            </div>
          </div>
        </div>
      </main>
    );
  }

  if (isLoading) {
    return (
      <main className="flex-1 p-6 overflow-y-auto bg-slate-950">
        <div className="max-w-7xl mx-auto space-y-6">
          <div className="h-12 bg-slate-900 rounded-xl animate-pulse" />
          <div className="h-64 bg-slate-900/60 rounded-xl animate-pulse" />
        </div>
      </main>
    );
  }

  if (isError || !details) {
    return (
      <main className="flex-1 p-6 overflow-y-auto bg-slate-950">
        <div className="max-w-7xl mx-auto space-y-4">
          <button
            onClick={() => navigate('/po')}
            className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Jobs</span>
          </button>
          <div className="p-6 bg-rose-500/10 border border-rose-500/20 rounded-xl text-rose-300 text-xs flex items-center gap-3">
            <AlertCircle className="w-5 h-5 shrink-0 text-rose-400" />
            <div>
              <p className="font-semibold text-sm">Failed to load PO details</p>
              <p className="text-rose-300/80 mt-0.5">
                {error instanceof Error ? error.message : 'Job not found or inaccessible.'}
              </p>
            </div>
            <button onClick={() => refetch()} className="ml-auto underline font-medium">
              Retry
            </button>
          </div>
        </div>
      </main>
    );
  }

  const tabs: Array<{ key: TabKey; label: string; icon: React.ComponentType<{ className?: string }> }> = [
    { key: 'overview', label: 'Overview', icon: FileSpreadsheet },
    { key: 'extracted', label: 'Extracted PO', icon: FileText },
    { key: 'line_items', label: 'Line Items', icon: Layers },
    { key: 'comparison', label: 'Email vs Attachment', icon: GitCompare },
    { key: 'hospital_history', label: 'Hospital History', icon: Building2 },
    { key: 'documents', label: 'Documents & OCR', icon: Cpu },
    { key: 'validation_audit', label: 'Validation & Audit', icon: ShieldCheck },
  ];

  return (
    <main className="flex-1 p-6 overflow-y-auto bg-slate-950">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Action Header */}
        <PoActionHeader details={details} onRefresh={refetch} />

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-800 space-x-1 overflow-x-auto no-scrollbar">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => setActiveTab(tab.key)}
                className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold whitespace-nowrap border-b-2 transition-colors ${
                  isActive
                    ? 'border-amber-500 text-amber-400 bg-amber-500/5'
                    : 'border-transparent text-slate-400 hover:text-slate-200 hover:border-slate-700'
                }`}
              >
                <Icon className={`w-3.5 h-3.5 ${isActive ? 'text-amber-400' : 'text-slate-500'}`} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Tab Panels */}
        <div>
          {activeTab === 'overview' && <PoOverviewTab details={details} />}
          {activeTab === 'extracted' && <PoExtractedDetailsTab details={details} onRefresh={refetch} />}
          {activeTab === 'line_items' && <PoLineItemsTab details={details} />}
          {activeTab === 'comparison' && <PoComparisonTab details={details} />}
          {activeTab === 'hospital_history' && <PoHospitalHistoryTab details={details} />}
          {activeTab === 'documents' && <PoDocumentsTab details={details} />}
          {activeTab === 'validation_audit' && <PoValidationAuditTab details={details} />}
        </div>
      </div>
    </main>
  );
};
