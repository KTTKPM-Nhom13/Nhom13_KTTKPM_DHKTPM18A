package com.cab.matching.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO nhận kết quả trả về từ AI Scoring Service (FastAPI).
 * Ánh xạ trực tiếp với response JSON của endpoint POST /api/v1/ai/score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiScoringResponse {

    /** ID của tài xế có điểm số cao nhất — đây là tài xế được gợi ý match. */
    private String bestDriverId;

    /** Điểm số cao nhất (để logging / monitoring). */
    private double highestScore;

    /**
     * Danh sách xếp hạng đầy đủ từ AI model — đã được sắp xếp từ điểm cao xuống thấp.
     * Dùng để vòng lặp Race Condition thử từng tài xế theo thứ tự ưu tiên.
     */
    private List<RankingEntry> ranking;
}

