package com.po.processor.document;

import com.po.processor.storage.StorageReader;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service responsible for retrieving and cleaning email body content from storage.
 */
@Service
public class EmailBodyExtractor {

    private static final Logger log = LoggerFactory.getLogger(EmailBodyExtractor.class);

    private final StorageReader storageReader;

    public EmailBodyExtractor(StorageReader storageReader) {
        this.storageReader = storageReader;
    }

    /**
     * Retrieves email body text, attempting plain text first, then HTML with conversion.
     *
     * @param strBodyTextStorageRef Optional storage reference for plain text body
     * @param strBodyHtmlStorageRef Optional storage reference for HTML body
     * @return Clean extracted email text
     */
    public String extractEmailBodyText(String strBodyTextStorageRef, String strBodyHtmlStorageRef) {
        if (strBodyTextStorageRef != null && !strBodyTextStorageRef.isBlank()) {
            try {
                String strText = storageReader.readText(strBodyTextStorageRef);
                if (strText != null && !strText.isBlank()) {
                    return strText.trim();
                }
            } catch (Exception exception) {
                log.warn("Failed reading plain text email body at ref: {}", strBodyTextStorageRef, exception);
            }
        }

        if (strBodyHtmlStorageRef != null && !strBodyHtmlStorageRef.isBlank()) {
            try {
                String strRawHtml = storageReader.readText(strBodyHtmlStorageRef);
                if (strRawHtml != null && !strRawHtml.isBlank()) {
                    return Jsoup.parse(strRawHtml).text().trim();
                }
            } catch (Exception exception) {
                log.warn("Failed reading HTML email body at ref: {}", strBodyHtmlStorageRef, exception);
            }
        }

        return "";
    }
}
