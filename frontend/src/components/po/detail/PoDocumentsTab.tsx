import React from 'react';
import { ProcessingJobFullDetails, PoDocumentAnalysis } from '../../../types/po';
import { FileText, Cpu, Hash } from 'lucide-react';

interface DocumentsTabProps {
  details: ProcessingJobFullDetails;
}

export const PoDocumentsTab: React.FC<DocumentsTabProps> = ({ details }) => {
  const analyses = details.documentAnalyses || [];

  return (
    <div className="space-y-6">
      {/* Attachments & Technical Extractions */}
      <div className="space-y-3">
        <h4 className="text-xs font-semibold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
          <FileText className="w-3.5 h-3.5 text-amber-400" />
          <span>Attachment Document Analyses</span>
        </h4>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {analyses.length === 0 ? (
            <div className="col-span-2 p-6 text-center text-slate-500 bg-slate-900/40 rounded-xl border border-slate-800">
              No attachment analyses available.
            </div>
          ) : (
            analyses.map((doc: PoDocumentAnalysis) => (
              <div key={doc.id} className="p-4 rounded-xl bg-slate-900/60 border border-slate-800 space-y-2.5 text-xs">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-white truncate max-w-[200px]" title={doc.originalFilename}>
                    {doc.originalFilename}
                  </span>
                  <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-sky-500/10 text-sky-400 border border-sky-500/20">
                    {doc.documentClassification}
                  </span>
                </div>

                <div className="space-y-1 text-slate-400 text-[11px]">
                  <p className="flex items-center gap-1.5">
                    <Cpu className="w-3 h-3 text-slate-500" />
                    <span>Method:</span>
                    <span className="text-slate-200 font-medium">{doc.extractionMethod}</span>
                  </p>
                  <p className="flex items-center gap-1.5">
                    <Hash className="w-3 h-3 text-slate-500" />
                    <span>SHA-256:</span>
                    <span className="font-mono text-slate-300 truncate">{doc.fileHash ? `${doc.fileHash.slice(0, 20)}...` : '—'}</span>
                  </p>
                  <p>Pages: <span className="text-slate-200">{doc.pageCount}</span> • Size: <span className="text-slate-200">{doc.fileSize ? `${Math.round(doc.fileSize / 1024)} KB` : '—'}</span></p>
                </div>

                {doc.extractedTextPreview && (
                  <div className="mt-2 p-2.5 rounded-lg bg-slate-950/80 border border-slate-800 text-[11px] font-mono text-slate-400 max-h-24 overflow-y-auto">
                    {doc.extractedTextPreview}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
