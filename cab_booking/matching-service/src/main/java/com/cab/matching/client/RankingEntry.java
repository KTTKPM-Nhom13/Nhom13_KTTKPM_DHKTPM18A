package com.cab.matching.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một entry trong danh sách xếp hạng trả về từ AI Scoring Service.
 * Ánh xạ với Pydantic model {@code DriverScore} bên Python.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingEntry {

    /** ID tài xế. */
    private String driverId;

    /** Điểm số AI tính toán (thang 0–100). */
    private double score;

    /** Chi tiết chuẩn hóa từng feature (debug / monitoring). */
    private String details;
}
