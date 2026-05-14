package iuh.fit.pricing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "eta-service",
        url = "${services.eta.url:http://eta-service:8088}"
)
public interface EtaClient {

    @PostMapping("${services.eta.estimate-path:/internal/eta/estimate}")
    EtaEstimateResponse estimate(@RequestBody EtaEstimateRequest request);

    record EtaEstimateRequest(
            double pickupLat,
            double pickupLng,
            double dropoffLat,
            double dropoffLng
    ) {
    }

    record EtaEstimateResponse(
            double distanceKm,
            int durationMinutes,
            String source
    ) {
    }
}
