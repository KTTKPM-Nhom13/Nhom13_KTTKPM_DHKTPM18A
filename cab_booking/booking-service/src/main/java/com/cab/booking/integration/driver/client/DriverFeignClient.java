package com.cab.booking.integration.driver.client;

import com.cab.booking.integration.driver.DriverDto;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign Client gọi sang Driver Service.
 * Khi Driver Service chưa có mặt, fallback trả về mock driver
 * để Booking Service vẫn chạy được end-to-end trong demo.
 */
@FeignClient(name = "driver-service", url = "${DRIVER_SERVICE_URL:http://localhost:8081}")
public interface DriverFeignClient {

    Logger log = LoggerFactory.getLogger(DriverFeignClient.class);

    @GetMapping("/api/drivers/nearby")
    @Retry(name = "driverService", fallbackMethod = "fallbackGetDrivers")
    List<DriverDto> getNearbyDrivers(@RequestParam("location") String location);

    /**
     * Fallback: trả về 1 mock driver cố định.
     * Trong demo, điều này giúp Booking Service không bị lỗi khi Driver Service offline.
     */
    default List<DriverDto> fallbackGetDrivers(String location, Throwable t) {
        log.warn("⚠️ [FEIGN FALLBACK] Driver Service offline. Trả mock driver. Error: {}", t.getMessage());
        return List.of(
                DriverDto.builder()
                        .id("drv-mock-fallback-001")
                        .name("Tài xế Mock")
                        .vehicleType("SEDAN")
                        .rating(4.8)
                        .build()
        );
    }
}
