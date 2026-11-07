import React, { useEffect, useState } from 'react';
import { EmailDetail, EmailProcessingStatus } from '../../types';
import { StatusBadge } from '../common/StatusBadge';
import { api } from '../../api/client';
import { X, Paperclip, Download, Cpu } from 'lucide-react';

interface EmailDetailDrawerProps {
  emailId: string | null;
  onClose: () => void;
}

export const EmailDetailDrawer: React.FC<EmailDetailDrawerProps> = ({ emailId, onClose }) => {
  const [detail, setDetail] = useState<EmailDetail | null>(null);
  const [statuses, setStatuses] = useState<EmailProcessingStatus[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!emailId) return;
    setLoading(true);
    Promise.all([api.getEmailDetail(emailId), api.getProcessingStatuses(emailId)])
      .then(([emailData, statusData]) => {
        setDetail(emailData);
        setStatuses(statusData);
      })
      .finally(() => setLoading(false));
  }, [emailId]);

  if (!emailId) return null;

  return (
    <div className="fixed inset-y-0 right-0 z-50 w-full max-w-xl bg-slate-900 border-l border-slate-800 shadow-2xl flex flex-col">
      <div className="p-5 border-b border-slate-800 flex items-center justify-between">
        <div>
          <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
            Email Message Detail
          </span>
          <h3 className="text-sm font-semibold text-white mt-0.5 truncate max-w-md">
            {detail?.subject || 'Loading...'}
          </h3>
        </div>
        <button onClick={onClose} className="text-slate-400 hover:text-slate-200">
          <X className="w-5 h-5" />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-6 text-xs text-slate-300">
        {loading || !detail ? (
          <div className="text-center py-12 text-slate-400">Loading email payload...</div>
        ) : (
          <>
            {/* Headers section */}
            <div className="bg-slate-800/40 border border-slate-800 rounded-xl p-4 space-y-2">
              <div className="flex justify-between">
                <span className="text-slate-400">From:</span>
                <span className="text-white font-medium">{detail.sender}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">To:</span>
                <span className="text-slate-200 font-mono text-[11px]">{detail.recipients?.join(', ')}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Mailbox:</span>
                <span className="text-slate-200">{detail.mailboxEmail}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Received:</span>
                <span className="text-slate-200">{new Date(detail.receivedAt).toLocaleString()}</span>
              </div>
              <div className="flex justify-between items-center pt-1 border-t border-slate-800/60">
                <span className="text-slate-400">Ingestion Status:</span>
                <StatusBadge status={detail.ingestionStatus} />
              </div>
            </div>

            {/* Downstream Consumer Processing Statuses */}
            <div>
              <div className="flex items-center gap-2 mb-2">
                <Cpu className="w-4 h-4 text-sky-400" />
                <h4 className="text-xs font-semibold text-white uppercase tracking-wider">
                  Downstream Processing Outcomes
                </h4>
              </div>
              <p className="text-[11px] text-slate-400 mb-3">
                Independent outcomes reported by registered business modules for this email.
              </p>

              {statuses.length === 0 ? (
                <div className="bg-slate-800/30 border border-slate-800 rounded-xl p-3 text-slate-400 text-center">
                  Awaiting acknowledgements from downstream consumers.
                </div>
              ) : (
                <div className="space-y-2">
                  {statuses.map((status) => (
                    <div
                      key={status.id}
                      className="bg-slate-800/50 border border-slate-700/60 rounded-xl p-3 flex items-start justify-between gap-3"
                    >
                      <div className="space-y-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-white">{status.consumerCode}</span>
                          <StatusBadge status={status.status} />
                        </div>
                        {status.message && (
                          <p className="text-[11px] text-slate-300">{status.message}</p>
                        )}
                        {status.referenceId && (
                          <p className="text-[10px] font-mono text-slate-400">
                            Reference: {status.referenceId}
                          </p>
                        )}
                      </div>
                      <span className="text-[10px] text-slate-400 shrink-0">
                        {status.processedAt ? new Date(status.processedAt).toLocaleTimeString() : ''}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Attachments Section */}
            <div>
              <div className="flex items-center gap-2 mb-2">
                <Paperclip className="w-4 h-4 text-sky-400" />
                <h4 className="text-xs font-semibold text-white uppercase tracking-wider">
                  Attachments ({detail.attachments?.length || 0})
                </h4>
              </div>
              {detail.attachments && detail.attachments.length > 0 ? (
                <div className="space-y-2">
                  {detail.attachments.map((att) => (
                    <div
                      key={att.id}
                      className="bg-slate-800/50 border border-slate-700/60 rounded-xl p-3 flex items-center justify-between"
                    >
                      <div>
                        <p className="font-medium text-white">{att.fileName}</p>
                        <p className="text-[10px] text-slate-400">
                          {(att.sizeBytes / 1024).toFixed(1)} KB &bull; {att.contentType}
                        </p>
                      </div>
                      <a
                        href={`/api/v1/emails/${detail.id}/attachments/${att.id}`}
                        download={att.fileName}
                        className="p-2 rounded-lg bg-slate-700/60 hover:bg-slate-700 text-sky-400 transition-colors"
                        title="Download Attachment from S3"
                      >
                        <Download className="w-3.5 h-3.5" />
                      </a>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-slate-400 italic">No attachments with this message.</p>
              )}
            </div>

            {/* Email Body Snippet */}
            <div>
              <h4 className="text-xs font-semibold text-white uppercase tracking-wider mb-2">
                Message Content
              </h4>
              <div className="bg-slate-800/40 border border-slate-800 rounded-xl p-4 font-mono text-[11px] text-slate-300 whitespace-pre-wrap leading-relaxed">
                {detail.snippet || 'No plain text snippet available.'}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
