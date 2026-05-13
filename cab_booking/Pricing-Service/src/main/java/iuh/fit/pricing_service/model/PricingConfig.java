package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "pricing_configs")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PricingConfig {

    @Id
    private String id;

    private String vehicleType;

    private Double baseFare;

    private Double perKmRate;

    private Double perMinuteRate;

    private Double multiplier;

    private Boolean active;

    private LocalDateTime updatedAt;

    private String schemaVersion;
}
