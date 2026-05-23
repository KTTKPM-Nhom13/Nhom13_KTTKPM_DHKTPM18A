package com.cab.ride.core.entity;

import com.cab.ride.core.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity đại diện cho một chuyến đi trong hệ thống Ride-Hailing.
 * Được lưu vào bảng {@code rides} của PostgreSQL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rides", indexes = {
        @Index(name = "idx_ride_customer_id",  columnList = "customerId"),
        @Index(name = "idx_ride_driver_id",    columnList = "driverId"),
        @Index(name = "idx_ride_status",       columnList = "status"),
        @Index(name = "idx_ride_created_at",   columnList = "createdAt"),
        @Index(name = "idx_ride_booking_id",   columnList = "bookingId", unique = true)
})
public class Ride {

    // ── Primary Key ────────────────────────────────────────────────────────
    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    // ── Optimistic Locking ─────────────────────────────────────────────────
    @Version
    private Integer version;

    // ── Participants ───────────────────────────────────────────────────────
    /** ID của khách hàng đặt cuốc. */
    @Column(length = 100)
    private String customerId;

    @Column(length = 100, unique = true)
    private String bookingId;

    /** ID của tài xế được chỉ định (null cho đến khi ASSIGNED). */
    @Column(length = 100)
    private String driverId;

    @Column(length = 255)
    private String pickupAddress;

    @Column(length = 255)
    private String dropoffAddress;

    // ── Coordinates ───────────────────────────────────────────────────────
    /** Vĩ độ điểm đón. */
    @Column
    private Double pickupLat;

    /** Kinh độ điểm đón. */
    @Column
    private Double pickupLng;

    /** Vĩ độ điểm đến. */
    @Column
    private Double dropoffLat;

    /** Kinh độ điểm đến. */
    @Column
    private Double dropoffLng;

    @Column(precision = 12, scale = 2)
    private BigDecimal finalFare;

    @Column(length = 50)
    private String paymentMethod;

    // ── State Machine ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RideStatus status = RideStatus.CREATED;

    // ── Audit ──────────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
