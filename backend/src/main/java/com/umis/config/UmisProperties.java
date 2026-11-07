package com.umis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for UMIS.
 */
@Configuration
@ConfigurationProperties(prefix = "umis")
public class UmisProperties {

    private Security security = new Security();
    private Storage storage = new Storage();
    private Rabbitmq rabbitmq = new Rabbitmq();
    private Sync sync = new Sync();

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(Rabbitmq rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public static class Security {
        private Jwt jwt = new Jwt();
        private String encryptionKey = "umis-default-secret-key-32-chars";
        private String previousEncryptionKey;
        private java.util.List<String> corsAllowedOrigins = java.util.List.of("http://localhost:3000");
        private boolean allowLocalImapHosts = false;

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public String getEncryptionKey() {
            return encryptionKey;
        }

        public void setEncryptionKey(String encryptionKey) {
            this.encryptionKey = encryptionKey;
        }

        public String getPreviousEncryptionKey() {
            return previousEncryptionKey;
        }

        public void setPreviousEncryptionKey(String previousEncryptionKey) {
            this.previousEncryptionKey = previousEncryptionKey;
        }

        public java.util.List<String> getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }

        public void setCorsAllowedOrigins(java.util.List<String> corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins;
        }

        public boolean isAllowLocalImapHosts() {
            return allowLocalImapHosts;
        }

        public void setAllowLocalImapHosts(boolean allowLocalImapHosts) {
            this.allowLocalImapHosts = allowLocalImapHosts;
        }

        public static class Jwt {
            private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
            private long expirationMs = 86400000L;

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }

            public long getExpirationMs() {
                return expirationMs;
            }

            public void setExpirationMs(long expirationMs) {
                this.expirationMs = expirationMs;
            }
        }
    }

    public static class Storage {
        private String type = "LOCAL";
        private String localBasePath = "./storage";
        private String endpoint = "http://localhost:9000";
        private String region = "us-east-1";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "umis-emails";
        private boolean pathStyleAccess = true;
        private long maxFileSizeBytes = 26214400L;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getLocalBasePath() {
            return localBasePath;
        }

        public void setLocalBasePath(String localBasePath) {
            this.localBasePath = localBasePath;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }
    }

    public static class Rabbitmq {
        private String exchange = "umis.events";
        private String queueEmailIngested = "umis.queue.email-ingested";
        private String queueDlq = "umis.queue.dlq";
        private String routingKeyPrefix = "umis.events";

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getQueueEmailIngested() {
            return queueEmailIngested;
        }

        public void setQueueEmailIngested(String queueEmailIngested) {
            this.queueEmailIngested = queueEmailIngested;
        }

        public String getQueueDlq() {
            return queueDlq;
        }

        public void setQueueDlq(String queueDlq) {
            this.queueDlq = queueDlq;
        }

        public String getRoutingKeyPrefix() {
            return routingKeyPrefix;
        }

        public void setRoutingKeyPrefix(String routingKeyPrefix) {
            this.routingKeyPrefix = routingKeyPrefix;
        }
    }

    public static class Sync {
        private int defaultPollingIntervalMinutes = 5;
        private int maxRetryAttempts = 3;
        private int lockExpiryMinutes = 15;

        public int getDefaultPollingIntervalMinutes() {
            return defaultPollingIntervalMinutes;
        }

        public void setDefaultPollingIntervalMinutes(int defaultPollingIntervalMinutes) {
            this.defaultPollingIntervalMinutes = defaultPollingIntervalMinutes;
        }

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        public int getLockExpiryMinutes() {
            return lockExpiryMinutes;
        }

        public void setLockExpiryMinutes(int lockExpiryMinutes) {
            this.lockExpiryMinutes = lockExpiryMinutes;
        }
    }
}
