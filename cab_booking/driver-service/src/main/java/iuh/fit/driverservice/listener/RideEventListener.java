package iuh.fit.driverservice.listener;

import iuh.fit.driverservice.dto.event.RideAcceptedEvent;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.event.RideCancelledEvent;
import iuh.fit.driverservice.service.DriverProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventListener {

    private final DriverProfileService driverProfileService;

    @KafkaListener(topics = "ride.assigned", groupId = "driver-service-group")
    public void handleRideAssigned(RideAssignedEvent event) {
        log.info("[ride.assigned] rideId={} | driverId={}", event.getRideId(), event.getDriverId());
        driverProfileService.handleRideAssigned(event);
    }

    @KafkaListener(topics = "ride.accepted", groupId = "driver-service-group")
    public void handleRideAccepted(RideAcceptedEvent event) {
        log.info("[ride.accepted] rideId={} | driverId={}", event.getRideId(), event.getDriverId());
        driverProfileService.handleRideAccepted(event);
    }

    @KafkaListener(topics = "ride.cancelled", groupId = "driver-service-group")
    public void handleRideCancelled(RideCancelledEvent event) {
        log.info("[ride.cancelled] rideId={} | driverId={} | reason={}",
                event.getRideId(),
                event.getDriverId(),
                event.getReason());
        driverProfileService.handleRideCancelled(event);
    }
}
