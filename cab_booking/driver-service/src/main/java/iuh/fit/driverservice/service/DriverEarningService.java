package iuh.fit.driverservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.driverservice.dto.event.PaymentCompletedEvent;
import iuh.fit.driverservice.entity.DriverEarning;
import iuh.fit.driverservice.entity.DriverProfile;
import iuh.fit.driverservice.repository.DriverEarningRepository;
import iuh.fit.driverservice.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverEarningService {

    private static final BigDecimal DRIVER_SHARE_PERCENT = new BigDecimal("70.00");
    private static final BigDecimal DRIVER_SHARE_RATE = new BigDecimal("0.70");
    private static final String CASH_PAYMENT_METHOD = "CASH";
    private static final String SETTLEMENT_ONLINE_GATEWAY_CREDIT = "ONLINE_GATEWAY_CREDIT";
    private static final String SETTLEMENT_CASH_PLATFORM_FEE_DEBIT = "CASH_PLATFORM_FEE_DEBIT";

    private final DriverProfileRepository driverProfileRepository;
    private final DriverEarningRepository driverEarningRepository;

    @Transactional
    public void creditDriverFromPayment(PaymentCompletedEvent event) {
        validateEvent(event);

        String rideId = resolveRideId(event);
        if (hasText(event.getEventId()) && driverEarningRepository.existsByPaymentEventId(event.getEventId())) {
            log.info("[DriverEarning] payment.completed already credited by eventId={}, rideId={}",
                    event.getEventId(), rideId);
            return;
        }
        if (driverEarningRepository.existsByRideIdAndDriverId(rideId, event.getDriverId())) {
            log.info("[DriverEarning] payment.completed already credited by rideId={}, driverId={}",
                    rideId, event.getDriverId());
            return;
        }

        DriverProfile profile = driverProfileRepository.findByExternalUserId(event.getDriverId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));

        BigDecimal grossAmount = event.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal driverAmount = grossAmount.multiply(DRIVER_SHARE_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformAmount = grossAmount.subtract(driverAmount).setScale(2, RoundingMode.HALF_UP);
        boolean cashPayment = isCashPayment(event.getPaymentMethod());
        BigDecimal balanceDelta = cashPayment ? platformAmount.negate() : driverAmount;
        String settlementType = cashPayment ? SETTLEMENT_CASH_PLATFORM_FEE_DEBIT : SETTLEMENT_ONLINE_GATEWAY_CREDIT;

        DriverEarning earning = new DriverEarning();
        earning.setPaymentEventId(event.getEventId());
        earning.setRideId(rideId);
        earning.setBookingId(event.getBookingId());
        earning.setDriverId(event.getDriverId());
        earning.setGrossAmount(grossAmount);
        earning.setDriverAmount(driverAmount);
        earning.setPlatformAmount(platformAmount);
        earning.setBalanceDelta(balanceDelta);
        earning.setSettlementType(settlementType);
        earning.setDriverSharePercent(DRIVER_SHARE_PERCENT);
        earning.setCurrency(hasText(event.getCurrency()) ? event.getCurrency() : "VND");
        earning.setPaymentMethod(event.getPaymentMethod());
        earning.setGatewayTransactionId(event.getGatewayTransactionId());
        driverEarningRepository.save(earning);

        BigDecimal currentEarnings = profile.getTotalEarnings() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : profile.getTotalEarnings();
        profile.setTotalEarnings(currentEarnings.add(balanceDelta).setScale(2, RoundingMode.HALF_UP));
        driverProfileRepository.save(profile);

        log.info("[DriverEarning] Settled driver earning - driverId={}, rideId={}, paymentMethod={}, settlementType={}, grossAmount={}, driverAmount={}, platformAmount={}, balanceDelta={}",
                event.getDriverId(), rideId, event.getPaymentMethod(), settlementType, grossAmount, driverAmount, platformAmount, balanceDelta);
    }

    private void validateEvent(PaymentCompletedEvent event) {
        if (event == null || !hasText(event.getDriverId()) || event.getAmount() == null
                || event.getAmount().compareTo(BigDecimal.ZERO) <= 0 || !hasText(resolveRideId(event))) {
            throw new AppException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String resolveRideId(PaymentCompletedEvent event) {
        return hasText(event.getRideId()) ? event.getRideId() : event.getBookingId();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isCashPayment(String paymentMethod) {
        return hasText(paymentMethod) && CASH_PAYMENT_METHOD.equalsIgnoreCase(paymentMethod.trim());
    }
}
