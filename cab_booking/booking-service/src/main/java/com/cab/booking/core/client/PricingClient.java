package com.cab.booking.core.client;

import com.cab.booking.common.BookingException;
import com.cab.booking.common.ErrorCode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@Slf4j
public class PricingClient {

    private static final String QUOTE_HASH_HEADER = "X-Quote-Hash";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PricingClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.services.pricing.base-url}") String pricingBaseUrl) {
        this.restClient = restClientBuilder
                .baseUrl(pricingBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "pricingService", fallbackMethod = "pricingFallback")
    @Retry(name = "pricingService")
    @Bulkhead(name = "pricingService")
    public PricingConfirmResponse confirmEstimate(String estimateId, String quotePayloadHash, String bearerToken) {
        try {
            return restClient.post()
                    .uri("/api/v1/pricing/confirm/{estimateId}", estimateId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(QUOTE_HASH_HEADER, quotePayloadHash)
                    .headers(headers -> setBearerToken(headers, bearerToken))
                    .retrieve()
                    .body(PricingConfirmResponse.class);
        } catch (RestClientResponseException ex) {
            throw mapPricingException(estimateId, ex);
        } catch (ResourceAccessException ex) {
            throw new PricingInfrastructureException("Pricing Service tam thoi khong kha dung", ex);
        }
    }

    public PricingConfirmResponse pricingFallback(
            String estimateId,
            String quotePayloadHash,
            String bearerToken,
            Throwable ex) {
        if (ex instanceof BookingException bookingException) {
            throw bookingException;
        }
        log.warn("Pricing confirm unavailable after resilience handling | estimateId={} | reason={}",
                estimateId, ex.getMessage());
        throw new BookingException(
                ErrorCode.PRICING_SERVICE_UNAVAILABLE,
                "Pricing confirm tam thoi khong kha dung. Vui long tao lai booking sau.",
                ex);
    }

    private void setBearerToken(HttpHeaders headers, String bearerToken) {
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(bearerToken);
        }
    }

    private BookingException mapPricingException(String estimateId, RestClientResponseException ex) {
        PricingErrorResponse errorResponse = parsePricingError(ex);
        String pricingError = errorResponse == null ? null : errorResponse.getError();
        String pricingMessage = errorResponse == null ? ex.getResponseBodyAsString() : errorResponse.getMessage();

        if (ex.getStatusCode().is5xxServerError()) {
            log.warn("Pricing confirm infrastructure failure - estimateId={}, status={}, message={}",
                    estimateId, ex.getStatusCode(), pricingMessage);
            throw new PricingInfrastructureException("Pricing Service returned " + ex.getStatusCode(), ex);
        }

        ErrorCode bookingError = switch (pricingError == null ? "" : pricingError) {
            case "QUOTE_HASH_MISMATCH" -> ErrorCode.QUOTE_HASH_MISMATCH;
            case "ESTIMATE_EXPIRED" -> ErrorCode.ESTIMATE_EXPIRED;
            case "INVALID_STATUS" -> ErrorCode.INVALID_QUOTE_STATUS;
            case "ESTIMATE_NOT_FOUND", "Not Found" -> ErrorCode.ESTIMATE_NOT_FOUND;
            default -> ErrorCode.QUOTE_CONFIRMATION_FAILED;
        };

        if (bookingError == ErrorCode.QUOTE_HASH_MISMATCH) {
            log.warn("Security quote mismatch from Pricing-Service - estimateId={}, status={}, message={}",
                    estimateId, ex.getStatusCode(), pricingMessage);
        } else {
            log.warn("Pricing confirm failed - estimateId={}, status={}, pricingError={}, message={}",
                    estimateId, ex.getStatusCode(), pricingError, pricingMessage);
        }

        return new BookingException(bookingError, bookingError.getDefaultMessage(), ex);
    }

    private PricingErrorResponse parsePricingError(RestClientResponseException ex) {
        try {
            return objectMapper.readValue(ex.getResponseBodyAsByteArray(), PricingErrorResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static class PricingInfrastructureException extends RuntimeException {
        public PricingInfrastructureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PricingConfirmResponse {
        private String id;
        private String quoteId;
        private String quotePayloadHash;
        private String quoteHashAlgorithm;
        private BigDecimal totalFare;
        private String currency;
        private String status;
        private LocalDateTime expiresAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class PricingErrorResponse {
        private String error;
        private String message;
    }
}
