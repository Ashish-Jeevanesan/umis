package com.po.processor.storage;

import java.io.InputStream;

/**
 * Value object representing a downloaded or streamed storage object.
 *
 * @param inputStream stream of content bytes (caller is responsible for closing)
 * @param contentType MIME type of the content
 * @param contentLength size in bytes (-1 if unknown)
 */
public record StorageObject(
        InputStream inputStream,
        String contentType,
        long contentLength
) {}
