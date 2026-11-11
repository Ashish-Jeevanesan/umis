package com.po.processor.document;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Validates image magic bytes and header dimensions without decoding full bitmaps into heap memory.
 */
@Component
public class ImageDocumentValidator {

    private static final Logger log = LoggerFactory.getLogger(ImageDocumentValidator.class);

    private final int maxDimension;

    public ImageDocumentValidator(@Value("${po.attachment.max-image-dimension:4096}") int maxDimension) {
        this.maxDimension = maxDimension;
    }

    /**
     * Validation outcome holder.
     */
    public record ImageValidationResult(
            boolean isValidImage,
            boolean isDimensionSafe,
            int width,
            int height,
            String formatName,
            String errorMessage
    ) {}

    /**
     * Inspects image bytes for valid image format and bounds-safe dimensions.
     *
     * @param byteData Raw image bytes
     * @return ImageValidationResult
     */
    public ImageValidationResult validateImage(byte[] byteData) {
        if (byteData == null || byteData.length < 8) {
            return new ImageValidationResult(false, false, 0, 0, null, "File too small to be a valid image");
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(byteData);
             ImageInputStream iis = ImageIO.createImageInputStream(bais)) {

            if (iis == null) {
                return new ImageValidationResult(false, false, 0, 0, null, "Cannot read image input stream");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return new ImageValidationResult(false, false, 0, 0, null, "Unsupported or corrupt image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                String strFormat = reader.getFormatName();

                boolean isSafe = width <= maxDimension && height <= maxDimension;
                String strError = isSafe ? null : "Image dimensions (" + width + "x" + height
                        + ") exceed maximum allowed limit of " + maxDimension + "px";

                return new ImageValidationResult(true, isSafe, width, height, strFormat, strError);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            log.warn("Failed inspecting image header dimensions: {}", exception.getMessage());
            return new ImageValidationResult(false, false, 0, 0, null, exception.getMessage());
        }
    }
}
