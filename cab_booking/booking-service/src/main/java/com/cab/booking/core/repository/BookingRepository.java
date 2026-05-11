package com.cab.booking.core.repository;

import com.cab.booking.core.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.idempotencyKey = :key")
    boolean existsByIdempotencyKey(@Param("key") String idempotencyKey);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    org.springframework.data.domain.Page<Booking> findByCustomerIdOrderByCreatedAtDesc(String customerId, org.springframework.data.domain.Pageable pageable);

    Optional<Booking> findFirstByCustomerIdAndStatusIn(String customerId, java.util.List<com.cab.booking.core.enums.BookingStatus> statuses);

    org.springframework.data.domain.Page<Booking> findByAssignedDriverIdOrderByCreatedAtDesc(String driverId, org.springframework.data.domain.Pageable pageable);

    java.util.List<Booking> findByStatus(com.cab.booking.core.enums.BookingStatus status);
}
