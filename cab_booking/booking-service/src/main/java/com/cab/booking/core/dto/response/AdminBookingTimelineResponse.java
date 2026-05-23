package com.cab.booking.core.dto.response;

import com.cab.booking.core.entity.AdminBookingAudit;
import com.cab.booking.core.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingTimelineResponse {
    private UUID id;
    private UUID bookingId;
    private String adminId;
    private String action;
    private BookingStatus oldStatus;
    private BookingStatus newStatus;
    private String reason;
    private String metadata;
    private LocalDateTime createdAt;

    public static AdminBookingTimelineResponse fromEntity(AdminBookingAudit audit) {
        return AdminBookingTimelineResponse.builder()
                .id(audit.getId())
                .bookingId(audit.getBookingId())
                .adminId(audit.getAdminId())
                .action(audit.getAction())
                .oldStatus(audit.getOldStatus())
                .newStatus(audit.getNewStatus())
                .reason(audit.getReason())
                .metadata(audit.getMetadata())
                .createdAt(audit.getCreatedAt())
                .build();
    }
}
