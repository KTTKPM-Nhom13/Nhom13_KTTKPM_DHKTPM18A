package iuh.fit.pricing_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "openMeteoClient", url = "https://api.open-meteo.com/v1")
public interface OpenMeteoClient {

    @GetMapping("/forecast")
    OpenMeteoResponse getForecast(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam("current") String current
    );

    record OpenMeteoResponse(
            double latitude,
            double longitude,
            CurrentWeather current
    ) {}

    record CurrentWeather(
            double temperature_2m,
            int weather_code
    ) {}
}
