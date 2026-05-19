package iuh.fit.driverservice.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Entity
@Table(
        name = "driver_earnings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_driver_earning_payment_event", columnNames = "payment_event_id"),
                @UniqueConstraint(name = "uk_driver_earning_ride_driver", columnNames = {"ride_id", "driver_id"})
        }
)
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DriverEarning extends BaseEntity {

    @Column(name = "payment_event_id", length = 100)
    String paymentEventId;

    @Column(name = "ride_id", nullable = false, length = 100)
    String rideId;

    @Column(name = "booking_id", length = 100)
    String bookingId;

    @Column(name = "driver_id", nullable = false, length = 100)
    String driverId;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal grossAmount;

    @Column(name = "driver_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal driverAmount;

    @Column(name = "platform_amount", nullable = false, precision = 18, scale = 2)
    BigDecimal platformAmount;

    @Column(name = "driver_share_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal driverSharePercent;

    @Column(length = 10)
    String currency;

    @Column(name = "payment_method", length = 30)
    String paymentMethod;

    @Column(name = "gateway_transaction_id", length = 100)
    String gatewayTransactionId;
}
