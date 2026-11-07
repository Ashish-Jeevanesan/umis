package com.umis.email.service;

import com.umis.common.exception.MailboxIntegrationException;
import com.umis.config.UmisProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * S3/MinIO-backed object storage client.
 */
@Component
@ConditionalOnProperty(prefix = "umis.storage", name = "type", havingValue = "S3")
public class S3ObjectStorageClient implements ObjectStorageClient {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageClient.class);

    private final S3Client s3Client;
    private final UmisProperties properties;

    public S3ObjectStorageClient(S3Client s3Client, UmisProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    public void ensureBucketExists() {
        String bucket = properties.getStorage().getBucketName();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ignored) {
            try {
                log.info("Creating object storage bucket: {}", bucket);
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (Exception createException) {
                log.warn("Could not auto-create S3 bucket {}: {}", bucket, createException.getMessage());
            }
        } catch (Exception exception) {
            log.warn("Could not verify S3 bucket {}: {}", bucket, exception.getMessage());
        }
    }

    @Override
    public void putObject(String key, InputStream content, long contentLength, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(properties.getStorage().getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(content, contentLength));
        } catch (Exception exception) {
            log.error("Failed to store object in S3 for key {}: {}", key, exception.getMessage());
            // Do not fail entire ingestion if S3 is down in local testing mode
        }
    }

    @Override
    public DownloadedObject getObject(String key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(properties.getStorage().getBucketName())
                    .key(key)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getRequest);
            String contentType = s3Stream.response().contentType();
            long contentLength = s3Stream.response().contentLength();
            return new DownloadedObject(s3Stream, contentType, contentLength);
        } catch (Exception exception) {
            throw new MailboxIntegrationException("Failed to retrieve file from storage: " + key, exception);
        }
    }
}
