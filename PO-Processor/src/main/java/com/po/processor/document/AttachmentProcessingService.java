package com.po.processor.document;

import com.po.processor.ai.dto.DocumentExtractionRequest;
import com.po.processor.entity.PoDocumentAnalysis;
import com.po.processor.entity.ProcessingJob;
import com.po.processor.entity.ProcessingJobAttachment;
import com.po.processor.exception.AttachmentSizeLimitExceededException;
import com.po.processor.repository.PoDocumentAnalysisRepository;
import com.po.processor.storage.StorageObject;
import com.po.processor.storage.StorageReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service orchestrating bounded attachment stream retrieval, validation, and format extraction.
 */
@Service
public class AttachmentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentProcessingService.class);

    private final BoundedAttachmentStreamReader streamReader;
    private final PdfBoxDocumentExtractor pdfExtractor;
    private final DocxDocumentExtractor docxExtractor;
    private final ImageDocumentValidator imageValidator;
    private final DocumentClassificationService classificationService;
    private final PoDocumentAnalysisRepository docAnalysisRepository;
    private final StorageReader storageReader;

    public AttachmentProcessingService(
            BoundedAttachmentStreamReader streamReader,
            PdfBoxDocumentExtractor pdfExtractor,
            DocxDocumentExtractor docxExtractor,
            ImageDocumentValidator imageValidator,
            DocumentClassificationService classificationService,
            PoDocumentAnalysisRepository docAnalysisRepository,
            StorageReader storageReader
    ) {
        this.streamReader = streamReader;
        this.pdfExtractor = pdfExtractor;
        this.docxExtractor = docxExtractor;
        this.imageValidator = imageValidator;
        this.classificationService = classificationService;
        this.docAnalysisRepository = docAnalysisRepository;
        this.storageReader = storageReader;
    }

    /**
     * Processes all attachments of a job with bounded streaming and format-specific safeguards.
     *
     * @param job Processing job entity
     * @return List of valid attachment inputs for AI extraction
     */
    public List<DocumentExtractionRequest.DocumentAttachmentInput> processJobAttachments(ProcessingJob job) {
        List<DocumentExtractionRequest.DocumentAttachmentInput> alResults = new ArrayList<>();
        long cumulativeBytes = 0;

        if (job.getAttachments() == null) {
            return alResults;
        }

        for (ProcessingJobAttachment att : job.getAttachments()) {
            try {
                StorageObject storageObject = storageReader.getObject(att.getStorageReference());
                BoundedAttachmentStreamReader.BoundedReadResult readRes;

                try (InputStream inputStream = storageObject.inputStream()) {
                    readRes = streamReader.readBounded(
                            inputStream,
                            att.getFileSize() != null ? att.getFileSize() : -1,
                            cumulativeBytes
                    );
                }

                cumulativeBytes += readRes.bytesRead();
                byte[] byteData = readRes.byteData();
                att.setFileHash(readRes.sha256Hex());

                DocumentExtractionRequest.DocumentAttachmentInput input = parseAttachmentContent(job, att, byteData, readRes.sha256Hex());
                if (input != null) {
                    alResults.add(input);
                }
            } catch (AttachmentSizeLimitExceededException sizeEx) {
                log.warn("Attachment [{}] rejected for job [{}]: {}", att.getOriginalFilename(), job.getId(), sizeEx.getMessage());
                recordAnalysis(job.getId(), att, "REJECTED_OVERSIZED", "NONE", false, sizeEx.getMessage(), null);
            } catch (Exception ex) {
                log.warn("Failed processing attachment [{}] for job [{}]: {}", att.getOriginalFilename(), job.getId(), ex.getMessage());
                recordAnalysis(job.getId(), att, "FAILED", "NONE", false, ex.getMessage(), null);
            }
        }

        return alResults;
    }

    private DocumentExtractionRequest.DocumentAttachmentInput parseAttachmentContent(
            ProcessingJob job,
            ProcessingJobAttachment att,
            byte[] byteData,
            String strHash
    ) {
        String strContentType = att.getContentType() != null ? att.getContentType().toLowerCase(Locale.ROOT) : "";
        String strFilename = att.getOriginalFilename() != null ? att.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String strExtractedText = "";
        String strMethod = "DIGITAL_TEXT_EXTRACTION";
        String strBase64Img = null;
        int pageCount = 1;

        if (strContentType.contains("pdf") || strFilename.endsWith(".pdf")) {
            PdfBoxDocumentExtractor.PdfExtractionResult pdfRes = pdfExtractor.extract(byteData);
            if (pdfRes.errorMessage() != null) {
                recordAnalysis(job.getId(), att, "FAILED_PDF", pdfRes.extractionMethod(), false, pdfRes.errorMessage(), null);
                return null;
            }
            strExtractedText = pdfRes.textContent();
            pageCount = pdfRes.pageCount();
            strMethod = pdfRes.extractionMethod();
            strBase64Img = pdfRes.base64ImageSample();
        } else if (strContentType.contains("word") || strFilename.endsWith(".docx")) {
            strExtractedText = docxExtractor.extract(byteData);
            strMethod = "DIGITAL_TEXT_EXTRACTION";
        } else if (strContentType.startsWith("image/") || strFilename.endsWith(".png") || strFilename.endsWith(".jpg") || strFilename.endsWith(".jpeg")) {
            ImageDocumentValidator.ImageValidationResult imgRes = imageValidator.validateImage(byteData);
            if (!imgRes.isDimensionSafe()) {
                recordAnalysis(job.getId(), att, "REJECTED_DIMENSIONS", "IMAGE_VISION_EXTRACTION", true, imgRes.errorMessage(), null);
                return null;
            }
            strMethod = "IMAGE_VISION_EXTRACTION";
            strBase64Img = java.util.Base64.getEncoder().encodeToString(byteData);
        }

        String strClass = classificationService.classifyDocument(att.getOriginalFilename(), strContentType, strExtractedText);
        recordAnalysis(job.getId(), att, strClass, strMethod, "IMAGE_VISION_EXTRACTION".equals(strMethod), null, strExtractedText);

        return new DocumentExtractionRequest.DocumentAttachmentInput(
                att.getId(), att.getOriginalFilename(), att.getContentType(),
                strExtractedText, strBase64Img, pageCount, strMethod
        );
    }

    private void recordAnalysis(
            java.util.UUID jobId,
            ProcessingJobAttachment att,
            String strClassification,
            String strMethod,
            boolean isOcrUsed,
            String strError,
            String strPreview
    ) {
        PoDocumentAnalysis analysis = new PoDocumentAnalysis(jobId, att.getOriginalFilename(), strClassification);
        analysis.setProcessingJobAttachmentId(att.getId());
        analysis.setMimeType(att.getContentType());
        analysis.setFileSize(att.getFileSize());
        analysis.setFileHash(att.getFileHash());
        analysis.setExtractionMethod(strMethod);
        analysis.setOcrUsed(isOcrUsed);
        analysis.setProcessingResult(strError != null ? "FAILED" : "SUCCESS");
        analysis.setErrorDetails(strError);
        if (strPreview != null) {
            analysis.setExtractedTextPreview(strPreview.length() > 500 ? strPreview.substring(0, 500) : strPreview);
        }
        docAnalysisRepository.save(analysis);
    }
}
