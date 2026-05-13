package com.cab.ride.core.entity;

import com.cab.ride.core.enums.RideStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

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
        @Index(name = "idx_ride_created_at",   columnList = "createdAt")
})
public class Ride {

    // ── Primary Key ────────────────────────────────────────────────────────
    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    // ── Optimistic Locking ─────────────────────────────────────────────────
    @Version
    private Integer version;

    // ── Participants ───────────────────────────────────────────────────────
    /** ID của khách hàng đặt cuốc. */
    @Column(nullable = false)
    private String customerId;

    /** ID của tài xế được chỉ định (null cho đến khi ASSIGNED). */
    private String driverId;

    // ── Coordinates ───────────────────────────────────────────────────────
    /** Vĩ độ điểm đón. */
    @Column(nullable = false)
    private Double pickupLat;

    /** Kinh độ điểm đón. */
    @Column(nullable = false)
    private Double pickupLng;

    /** Vĩ độ điểm đến. */
    @Column(nullable = false)
    private Double dropoffLat;

    /** Kinh độ điểm đến. */
    @Column(nullable = false)
    private Double dropoffLng;

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
