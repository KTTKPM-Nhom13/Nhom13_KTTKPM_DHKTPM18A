package com.cab.booking.core.repository;

import com.cab.booking.core.entity.AdminBookingAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminBookingAuditRepository extends JpaRepository<AdminBookingAudit, UUID> {
    List<AdminBookingAudit> findByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
