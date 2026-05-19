package com.cab.ride.core.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CompleteRideRequest {
    private BigDecimal finalFare;
    private String paymentMethod;
}
