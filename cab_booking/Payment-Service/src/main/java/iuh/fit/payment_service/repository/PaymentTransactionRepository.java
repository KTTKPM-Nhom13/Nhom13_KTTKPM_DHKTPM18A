package iuh.fit.payment_service.repository;

import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransaction> findByBookingId(String bookingId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.bookingId = :bookingId AND p.status = :status")
    Optional<PaymentTransaction> findByBookingIdAndStatus(
            @Param("bookingId") String bookingId,
            @Param("status") PaymentStatus status
    );

    @Query("SELECT p FROM PaymentTransaction p WHERE p.customerId = :customerId ORDER BY p.createdAt DESC")
    List<PaymentTransaction> findByCustomerIdOrderByCreatedAtDesc(@Param("customerId") String customerId);

    @Query("SELECT p FROM PaymentTransaction p WHERE p.status IN :statuses ORDER BY p.createdAt DESC")
    List<PaymentTransaction> findByStatusInOrderByCreatedAtDesc(@Param("statuses") List<PaymentStatus> statuses);

    @Query("SELECT COUNT(p) > 0 FROM PaymentTransaction p WHERE p.idempotencyKey = :key AND p.status = :status")
    boolean existsByIdempotencyKeyAndStatus(
            @Param("key") String idempotencyKey,
            @Param("status") PaymentStatus status
    );
}
