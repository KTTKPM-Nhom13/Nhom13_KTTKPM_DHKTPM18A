package iuh.fit.pricing_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "revenue_aggregations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "zone_date_idx", def = "{'zoneId': 1, 'date': 1}")
public class ZoneRevenueAggregate {

    @Id
    private String id;

    private String zoneId;

    private String date;

    private BigDecimal revenue;

    private Long trips;

    private BigDecimal averageFare;

    private LocalDateTime computedAt;
}
