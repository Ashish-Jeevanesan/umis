import React from 'react';
import { ProcessingJobFullDetails, PoValidationResult, PoFieldEditHistory, PoApprovalHistory } from '../../../types/po';
import { CheckCircle2, AlertOctagon, History, ShieldCheck } from 'lucide-react';

interface ValidationAuditTabProps {
  details: ProcessingJobFullDetails;
}

export const PoValidationAuditTab: React.FC<ValidationAuditTabProps> = ({ details }) => {
  const validations = details.validationResults || [];
  const editHistory = details.editHistory || [];
  const approvalHistory = details.approvalHistory || [];

  return (
    <div className="space-y-6">
      {/* Validation Rule Results */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
          <ShieldCheck className="w-3.5 h-3.5 text-teal-400" />
          <span>Automated Validation Rule Checks</span>
        </h4>

        <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] text-slate-400 font-semibold uppercase">
                <th className="py-2.5 px-3">Rule Name & Code</th>
                <th className="py-2.5 px-3">Severity</th>
                <th className="py-2.5 px-3">Outcome</th>
                <th className="py-2.5 px-3">Explanation</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {validations.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-slate-500">No validation results recorded.</td>
                </tr>
              ) : (
                validations.map((val: PoValidationResult) => (
                  <tr key={val.id || val.ruleCode} className="hover:bg-slate-800/30">
                    <td className="py-2.5 px-3">
                      <p className="font-medium text-white">{val.ruleName}</p>
                      <p className="text-[10px] text-slate-500 font-mono">{val.ruleCode}</p>
                    </td>
                    <td className="py-2.5 px-3">
                      <span className={`px-2 py-0.5 rounded-full text-[10px] font-semibold ${
                        val.severity === 'ERROR' ? 'bg-rose-500/10 text-rose-400' : 'bg-amber-500/10 text-amber-400'
                      }`}>
                        {val.severity}
                      </span>
                    </td>
                    <td className="py-2.5 px-3">
                      {val.passed ? (
                        <span className="inline-flex items-center gap-1 text-emerald-400 font-medium">
                          <CheckCircle2 className="w-3.5 h-3.5" />
                          <span>Passed</span>
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-rose-400 font-medium">
                          <AlertOctagon className="w-3.5 h-3.5" />
                          <span>Failed</span>
                        </span>
                      )}
                    </td>
                    <td className="py-2.5 px-3 text-slate-400 text-[11px]">{val.explanation || '—'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Field Edit History */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
          <History className="w-3.5 h-3.5 text-amber-400" />
          <span>Field Modification Audit History</span>
        </h4>

        <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] text-slate-400 font-semibold uppercase">
                <th className="py-2.5 px-3">Field</th>
                <th className="py-2.5 px-3">Original</th>
                <th className="py-2.5 px-3">New Value</th>
                <th className="py-2.5 px-3">Edited By</th>
                <th className="py-2.5 px-3">Reason</th>
                <th className="py-2.5 px-3">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {editHistory.length === 0 ? (
                <tr>
                  <td colSpan={6} className="py-6 text-center text-slate-500">No human edits recorded.</td>
                </tr>
              ) : (
                editHistory.map((edit: PoFieldEditHistory) => (
                  <tr key={edit.id} className="hover:bg-slate-800/30">
                    <td className="py-2.5 px-3 font-semibold text-white">{edit.fieldName}</td>
                    <td className="py-2.5 px-3 font-mono text-slate-400">{edit.originalValue || '—'}</td>
                    <td className="py-2.5 px-3 font-mono text-emerald-400 font-medium">{edit.newValue || '—'}</td>
                    <td className="py-2.5 px-3 text-slate-300">{edit.editedBy}</td>
                    <td className="py-2.5 px-3 text-slate-400 text-[11px]">{edit.editReason || '—'}</td>
                    <td className="py-2.5 px-3 text-slate-500 text-[11px]">{new Date(edit.editedAt).toLocaleString()}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Approval & Action History */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
          <CheckCircle2 className="w-3.5 h-3.5 text-cyan-400" />
          <span>Workflow Approval History</span>
        </h4>

        <div className="rounded-xl border border-slate-800 overflow-hidden bg-slate-900/40">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-950/60 text-[11px] text-slate-400 font-semibold uppercase">
                <th className="py-2.5 px-3">Action</th>
                <th className="py-2.5 px-3">Actor</th>
                <th className="py-2.5 px-3">Comments</th>
                <th className="py-2.5 px-3">Timestamp</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60 text-slate-300">
              {approvalHistory.length === 0 ? (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-slate-500">No workflow actions recorded yet.</td>
                </tr>
              ) : (
                approvalHistory.map((app: PoApprovalHistory) => (
                  <tr key={app.id} className="hover:bg-slate-800/30">
                    <td className="py-2.5 px-3 font-semibold text-white">{app.action}</td>
                    <td className="py-2.5 px-3 text-slate-300">{app.actionBy}</td>
                    <td className="py-2.5 px-3 text-slate-400 text-[11px]">{app.comments || '—'}</td>
                    <td className="py-2.5 px-3 text-slate-500 text-[11px]">{new Date(app.actionAt).toLocaleString()}</td>
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
