package com.umis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * AWS SDK S3 client configuration supporting both MinIO and AWS S3 object storage.
 */
@Configuration
public class S3StorageConfig {

    private final UmisProperties properties;

    public S3StorageConfig(UmisProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnProperty(prefix = "umis.storage", name = "type", havingValue = "S3")
    public S3Client s3Client() {
        UmisProperties.Storage storage = properties.getStorage();
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                storage.getAccessKey(),
                storage.getSecretKey()
        );

        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.isPathStyleAccess())
                        .build());

        if (storage.getEndpoint() != null && !storage.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.getEndpoint()));
        }

        return builder.build();
    }
}
