package iuh.fit.pricing_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "pricing")
@Data
public class PricingConfigProperties {

    private Calculation calculation = new Calculation();
    private Map<String, VehicleConfig> vehicle = new java.util.HashMap<>();
    private Surge surge = new Surge();
    private Weather weather = new Weather();
    private Cache cache = new Cache();
    private Eta eta = new Eta();

    @Data
    public static class Calculation {
        private BigDecimal baseFare = new BigDecimal("2.50");
        private BigDecimal perKmRate = new BigDecimal("1.50");
        private BigDecimal perMinuteRate = new BigDecimal("0.25");
        private BigDecimal minimumFare = new BigDecimal("5.00");
        private BigDecimal platformFee = BigDecimal.ZERO;
        private BigDecimal airportFee = BigDecimal.ZERO;
        private BigDecimal tollFee = BigDecimal.ZERO;
        private String currency = "VND";
        private String configVersion = "v1";
    }

    @Data
    public static class VehicleConfig {
        private BigDecimal multiplier = BigDecimal.ONE;
        private BigDecimal baseFare = new BigDecimal("2.50");
        private BigDecimal perKm = new BigDecimal("1.50");
        private BigDecimal perMinute = new BigDecimal("0.20");
    }

    @Data
    public static class Surge {
        private BigDecimal defaultMultiplier = BigDecimal.ONE;
        private BigDecimal minMultiplier = BigDecimal.ONE;
        private BigDecimal maxMultiplier = new BigDecimal("3.0");
        private BigDecimal updateThreshold = new BigDecimal("0.1");
        private int cacheTtlSeconds = 60;
        private long schedulerFixedDelayMs = 60000;
        private int metricsTtlSeconds = 600;
        private BigDecimal highDemandRatio = new BigDecimal("1.5");
        private BigDecimal veryHighDemandRatio = new BigDecimal("3.0");
        private BigDecimal highDemandAdjustment = new BigDecimal("0.2");
        private BigDecimal veryHighDemandAdjustment = new BigDecimal("0.5");
        private BigDecimal rushHourAdjustment = new BigDecimal("0.2");
        private BigDecimal nightAdjustment = new BigDecimal("0.1");
        private BigDecimal manualAdjustment = BigDecimal.ZERO;
    }

    @Data
    public static class Weather {
        private BigDecimal badWeatherAdjustment = new BigDecimal("0.2");
    }

    @Data
    public static class Cache {
        private int weatherTtlSeconds = 1800;
        private int routeTtlSeconds = 300;
    }

    @Data
    public static class Eta {
        private int fallbackDurationMinutes = 15;
    }
}
