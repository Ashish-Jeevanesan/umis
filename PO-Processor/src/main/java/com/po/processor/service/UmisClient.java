package com.po.processor.service;

import com.po.processor.dto.UmisAcknowledgementRequest;
import com.po.processor.dto.UmisAcknowledgementResponse;
import com.po.processor.exception.EventProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Dedicated REST client for interacting with UMIS endpoints, including acknowledgements.
 */
@Service
public class UmisClient {

    private static final Logger log = LoggerFactory.getLogger(UmisClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String consumerId;
    private final String authEmail;
    private final String authPassword;

    private String cachedJwtToken;

    public UmisClient(
            @Value("${umis.base-url:http://localhost:8080}") String baseUrl,
            @Value("${umis.consumer-id:PO_PROCESSING}") String consumerId,
            @Value("${umis.auth.email:}") String authEmail,
            @Value("${umis.auth.password:}") String authPassword
    ) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.consumerId = consumerId;
        this.authEmail = authEmail;
        this.authPassword = authPassword;
    }

    /**
     * Submits an acknowledgement to UMIS for the specified email.
     */
    public UmisAcknowledgementResponse acknowledge(UUID emailId, UmisAcknowledgementRequest request) {
        String endpoint = String.format("%s/api/v1/emails/%s/acknowledgements", baseUrl, emailId);
        HttpHeaders headers = createHeaders();
        HttpEntity<UmisAcknowledgementRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Sending UMIS acknowledgement for email {} with status {}", emailId, request.status());
            ResponseEntity<UmisAcknowledgementResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    UmisAcknowledgementResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException statusException) {
            if (statusException.getStatusCode().value() == 401 && StringUtils.hasText(authPassword)) {
                log.info("Received 401 Unauthorized from UMIS. Refreshing JWT token and retrying...");
                this.cachedJwtToken = null;
                HttpHeaders retryHeaders = createHeaders();
                HttpEntity<UmisAcknowledgementRequest> retryEntity = new HttpEntity<>(request, retryHeaders);
                ResponseEntity<UmisAcknowledgementResponse> retryResponse = restTemplate.exchange(
                        endpoint, HttpMethod.POST, retryEntity, UmisAcknowledgementResponse.class);
                return retryResponse.getBody();
            }
            log.error("UMIS acknowledgement failed with status {}: {}",
                    statusException.getStatusCode(), statusException.getResponseBodyAsString());
            throw new EventProcessingException("UMIS acknowledgement failed: " + statusException.getMessage(),
                    statusException);
        } catch (Exception ex) {
            log.error("Network or connection error during UMIS acknowledgement: {}", ex.getMessage());
            String errorMsg = "Failed to connect to UMIS for acknowledgement: " + ex.getMessage();
            throw new EventProcessingException(errorMsg, ex);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String token = getOrFetchJwtToken();
        if (StringUtils.hasText(token)) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private synchronized String getOrFetchJwtToken() {
        if (StringUtils.hasText(this.cachedJwtToken)) {
            return this.cachedJwtToken;
        }

        if (!StringUtils.hasText(this.authEmail) || !StringUtils.hasText(this.authPassword)) {
            return null;
        }

        try {
            String loginUrl = baseUrl + "/api/v1/auth/login";
            Map<String, String> loginBody = Map.of("email", this.authEmail, "password", this.authPassword);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    loginUrl, new HttpEntity<>(loginBody, headers), Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");
                if (data != null && data.get("token") != null) {
                    this.cachedJwtToken = (String) data.get("token");
                    log.info("Successfully authenticated with UMIS as {}", this.authEmail);
                    return this.cachedJwtToken;
                }
            }
        } catch (Exception ex) {
            log.warn("Could not authenticate with UMIS: {}. Proceeding without Bearer token.", ex.getMessage());
        }
        return null;
    }

    public String getConsumerId() {
        return consumerId;
    }
}
