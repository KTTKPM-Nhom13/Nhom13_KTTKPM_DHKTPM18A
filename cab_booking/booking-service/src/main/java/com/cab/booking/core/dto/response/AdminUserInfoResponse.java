package com.cab.booking.core.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserInfoResponse {
    private String userId;
    private String fullName;
    private String phoneNumber;
}
