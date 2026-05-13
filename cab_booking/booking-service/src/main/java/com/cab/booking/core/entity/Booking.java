package com.cab.booking.core.entity;

import com.cab.booking.core.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import iuh.fit.common.model.BaseEntity;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_customer_id", columnList = "customerId"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_idempotency_key", columnList = "idempotencyKey", unique = true)
})
public class Booking extends BaseEntity {

    @Version
    private Integer version;

    @Column(nullable = false)
    private String customerId;

    private String assignedDriverId;

    @Column(nullable = false)
    private String pickupLocation;

    @Column(nullable = false)
    private String dropoffLocation;

    @Column(length = 1000)
    private String customerNote;

    // Tọa độ pickup — tách thành 2 cột double để query & index dễ dàng
    @Column
    private Double pickupLat;

    @Column
    private Double pickupLng;

    // Tọa độ dropoff — tách thành 2 cột double
    @Column
    private Double dropoffLat;

    @Column
    private Double dropoffLng;

    private String vehicleType;
    private String paymentMethod;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedFare;

    private String promoCode;

    // Zero Trust — lưu token để verify giá
    @Column(length = 2000)
    private String quoteToken;

    // Idempotency — unique constraint trên DB tránh trùng cuốc
    @Column(unique = true, length = 64)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BookingStatus status = BookingStatus.CREATED;
}
