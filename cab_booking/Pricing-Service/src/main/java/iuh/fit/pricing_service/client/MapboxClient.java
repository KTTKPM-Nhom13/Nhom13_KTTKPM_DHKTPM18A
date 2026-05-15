package iuh.fit.pricing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "mapboxClient", url = "https://api.mapbox.com/directions-matrix/v1/mapbox/driving")
public interface MapboxClient {

    @GetMapping("/{coordinates}")
    MapboxMatrixResponse getDistanceMatrix(
            @PathVariable("coordinates") String coordinates,
            @RequestParam("annotations") String annotations,
            @RequestParam("access_token") String accessToken
    );

    record MapboxMatrixResponse(
            String code,
            double[][] distances,
            double[][] durations
    ) {}
}
