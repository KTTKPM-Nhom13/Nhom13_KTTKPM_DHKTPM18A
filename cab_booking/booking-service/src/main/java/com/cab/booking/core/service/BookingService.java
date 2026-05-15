package com.cab.booking.core.service;

import com.cab.booking.core.dto.request.BookingRequest;
import com.cab.booking.core.dto.response.BookingResponse;

import java.util.UUID;

public interface BookingService {
    
    BookingResponse createRide(String customerId, BookingRequest request);
    
    BookingResponse acceptRide(UUID bookingId, String driverId);

    BookingResponse rejectAssignedRide(UUID bookingId, String driverId, String reason);
    
    BookingResponse startRide(UUID bookingId);
    
    BookingResponse completeRide(UUID bookingId);

    // ==========================================
    // CÁC API BỔ SUNG CHO KHÁCH HÀNG & TÀI XẾ
    // ==========================================

    org.springframework.data.domain.Page<BookingResponse> getCustomerHistory(String customerId, int page, int size);

    BookingResponse getActiveBookingByCustomer(String customerId);

    BookingResponse cancelRide(java.util.UUID bookingId, String reason);

    BookingResponse arriveAtPickup(UUID bookingId);

    org.springframework.data.domain.Page<BookingResponse> getDriverHistory(String driverId, int page, int size);

    java.util.List<BookingResponse> getNearbyMatchingBookings(double lat, double lng, double radiusKm);
}
