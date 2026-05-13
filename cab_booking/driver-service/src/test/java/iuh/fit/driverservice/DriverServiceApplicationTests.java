package iuh.fit.driverservice;

import iuh.fit.driverservice.dto.event.DriverStatusEvent;
import iuh.fit.driverservice.dto.event.RideAssignedEvent;
import iuh.fit.driverservice.dto.request.HandleDriverAssignmentRequest;
import iuh.fit.driverservice.dto.request.UpdateDriverAvailabilityRequest;
import iuh.fit.driverservice.dto.request.UpsertDriverProfileRequest;
import iuh.fit.driverservice.dto.response.DriverAvailabilityResponse;
import iuh.fit.driverservice.dto.response.DriverStatusCheckResponse;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import iuh.fit.driverservice.service.DriverProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:driver-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DriverServiceApplicationTests {
    @Autowired
    DriverProfileService driverProfileService;

    @Autowired
    DriverProfileRepository driverProfileRepository;

    @MockBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void updateAvailabilityShouldPersistGoogleMapCoordinatesAndPublishDriverStatus() {
        UpsertDriverProfileRequest profileRequest = new UpsertDriverProfileRequest();
        profileRequest.setFullName("Driver One");
        profileRequest.setEmail("driver1@example.com");
        profileRequest.setPhoneNumber("0900000001");
        profileRequest.setLicenseNumber("LIC-001");
        profileRequest.setVehicleType("CAR");
        profileRequest.setVehiclePlate("51A-12345");

        driverProfileService.upsertProfile("driver-1", profileRequest);

        UpdateDriverAvailabilityRequest request = new UpdateDriverAvailabilityRequest();
        request.setAvailabilityStatus("ONLINE");
        request.setCurrentLatitude(new BigDecimal("10.762622"));
        request.setCurrentLongitude(new BigDecimal("106.660172"));

        DriverAvailabilityResponse response = driverProfileService.updateAvailability("driver-1", request);

        DriverProfile savedProfile = driverProfileRepository.findByExternalUserId("driver-1").orElseThrow();
        assertThat(response.getCurrentLatitude()).isEqualByComparingTo("10.762622");
        assertThat(response.getCurrentLongitude()).isEqualByComparingTo("106.660172");
        assertThat(savedProfile.getCurrentLatitude()).isEqualByComparingTo("10.762622");
        assertThat(savedProfile.getCurrentLongitude()).isEqualByComparingTo("106.660172");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq("driver.status.changed"), eventCaptor.capture());
        DriverStatusEvent event = (DriverStatusEvent) eventCaptor.getValue();
        assertThat(event.getDriverId()).isEqualTo("driver-1");
        assertThat(event.getAvailabilityStatus()).isEqualTo("ONLINE");
        assertThat(event.getCurrentLocation().getLat()).isEqualByComparingTo("10.762622");
        assertThat(event.getCurrentLocation().getLng()).isEqualByComparingTo("106.660172");
        assertThat(event.getActiveForBooking()).isTrue();
    }

    @Test
    void handleAssignmentShouldPublishRideAssignedForBookingService() {
        UpsertDriverProfileRequest profileRequest = new UpsertDriverProfileRequest();
        profileRequest.setFullName("Driver Two");
        profileRequest.setEmail("driver2@example.com");
        profileRequest.setPhoneNumber("0900000002");
        profileRequest.setLicenseNumber("LIC-002");
        profileRequest.setVehicleType("BIKE");
        profileRequest.setVehiclePlate("59B-67890");

        driverProfileService.upsertProfile("driver-2", profileRequest);

        UpdateDriverAvailabilityRequest availabilityRequest = new UpdateDriverAvailabilityRequest();
        availabilityRequest.setAvailabilityStatus("ONLINE");
        availabilityRequest.setCurrentLatitude(new BigDecimal("10.776889"));
        availabilityRequest.setCurrentLongitude(new BigDecimal("106.700806"));
        driverProfileService.updateAvailability("driver-2", availabilityRequest);

        HandleDriverAssignmentRequest assignmentRequest = new HandleDriverAssignmentRequest();
        assignmentRequest.setRideId("2f52e5f8-0f91-4801-98db-ece474d38a13");
        assignmentRequest.setAction("ACCEPT");
        assignmentRequest.setPickupAddress("1 Vo Van Ngan, Thu Duc");
        assignmentRequest.setDestinationAddress("268 Ly Thuong Kiet, District 10");

        driverProfileService.handleAssignment("driver-2", assignmentRequest);

        ArgumentCaptor<Object> rideAssignedCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("ride.assigned"), rideAssignedCaptor.capture());
        RideAssignedEvent event = (RideAssignedEvent) rideAssignedCaptor.getValue();
        assertThat(event.getRideId()).isEqualTo("2f52e5f8-0f91-4801-98db-ece474d38a13");
        assertThat(event.getDriverId()).isEqualTo("driver-2");
        assertThat(event.getType()).isEqualTo(RideAssignedEvent.EVENT_TYPE);
    }

    @Test
    void checkAvailabilityShouldReturnOnlineFlagsAndPublishDriverStatusSnapshot() {
        UpsertDriverProfileRequest profileRequest = new UpsertDriverProfileRequest();
        profileRequest.setFullName("Driver Three");
        profileRequest.setEmail("driver3@example.com");
        profileRequest.setPhoneNumber("0900000003");
        profileRequest.setLicenseNumber("LIC-003");
        profileRequest.setVehicleType("CAR");
        profileRequest.setVehiclePlate("60A-11111");
        profileRequest.setVehicleModel("Hyundai Accent");
        profileRequest.setVehicleColor("White");

        driverProfileService.upsertProfile("driver-3", profileRequest);

        UpdateDriverAvailabilityRequest availabilityRequest = new UpdateDriverAvailabilityRequest();
        availabilityRequest.setAvailabilityStatus("ONLINE");
        availabilityRequest.setCurrentLatitude(new BigDecimal("10.780000"));
        availabilityRequest.setCurrentLongitude(new BigDecimal("106.690000"));
        driverProfileService.updateAvailability("driver-3", availabilityRequest);

        DriverStatusCheckResponse response = driverProfileService.checkAvailability("driver-3");

        assertThat(response.getExternalUserId()).isEqualTo("driver-3");
        assertThat(response.getAvailabilityStatus()).isEqualTo("ONLINE");
        assertThat(response.isOnline()).isTrue();
        assertThat(response.isOffline()).isFalse();
        assertThat(response.isActiveForBooking()).isTrue();

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq("driver.status.changed"), eventCaptor.capture());
        DriverStatusEvent event = (DriverStatusEvent) eventCaptor.getValue();
        assertThat(event.getDriverId()).isEqualTo("driver-3");
        assertThat(event.getAvailabilityStatus()).isEqualTo("ONLINE");
        assertThat(event.getActiveForBooking()).isTrue();
    }
}
