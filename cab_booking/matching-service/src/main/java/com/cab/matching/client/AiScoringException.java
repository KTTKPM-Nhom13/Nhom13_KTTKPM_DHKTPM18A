package com.cab.matching.client;

/**
 * Exception dành riêng cho lỗi từ AI Scoring Service.
 *
 * <p>Được ném bởi {@link FeignConfig.AiScoringErrorDecoder} khi:
 * <ul>
 *   <li>HTTP 422 — dữ liệu đầu vào không hợp lệ theo schema FastAPI</li>
 *   <li>HTTP 4xx khác — lỗi phía client không thể retry</li>
 * </ul>
 *
 * <p>Caller nên catch exception này để fallback về thuật toán matching truyền thống.
 */
public class AiScoringException extends RuntimeException {

    public AiScoringException(String message) {
        super(message);
    }

    public AiScoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
