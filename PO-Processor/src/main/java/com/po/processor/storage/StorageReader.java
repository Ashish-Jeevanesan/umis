package com.po.processor.storage;

/**
 * Read-only storage abstraction for accessing UMIS-managed email bodies and attachments.
 */
public interface StorageReader {

    /**
     * Checks if the object exists at the specified storage reference.
     *
     * @param storageReference storage key or relative path
     * @return true if the object exists and is accessible
     */
    boolean exists(String storageReference);

    /**
     * Opens a streaming connection to the object at the specified storage reference.
     *
     * @param storageReference storage key or relative path
     * @return StorageObject wrapping the open InputStream, content type, and size
     */
    StorageObject getObject(String storageReference);

    /**
     * Reads a small text object (such as email body text/HTML) into a string.
     *
     * @param storageReference storage key or relative path
     * @return UTF-8 decoded content string
     */
    String readText(String storageReference);
}
