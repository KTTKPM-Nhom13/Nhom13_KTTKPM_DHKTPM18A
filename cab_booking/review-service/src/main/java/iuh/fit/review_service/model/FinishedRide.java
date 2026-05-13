package iuh.fit.review_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "finished_rides")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FinishedRide {
    @Id
    private String rideId;
    private String customerId;
    private String driverId;
    private LocalDateTime finishedAt;
}
