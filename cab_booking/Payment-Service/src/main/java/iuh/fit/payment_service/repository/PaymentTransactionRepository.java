package iuh.fit.payment_service.repository;

import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    @Query("""
            SELECT p FROM PaymentTransaction p
            WHERE p.status = :status
              AND p.paymentMethod = :paymentMethod
              AND p.createdAt < :createdBefore
            ORDER BY p.createdAt DESC
            """)
    List<PaymentTransaction> findStalePendingByPaymentMethod(
            @Param("status") PaymentStatus status,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("createdBefore") Instant createdBefore,
            Pageable pageable
    );

    @Query("SELECT COUNT(p) > 0 FROM PaymentTransaction p WHERE p.idempotencyKey = :key AND p.status = :status")
    boolean existsByIdempotencyKeyAndStatus(
            @Param("key") String idempotencyKey,
            @Param("status") PaymentStatus status
    );

    long countByStatus(PaymentStatus status);

    @Query(value = """
        SELECT TO_CHAR(p.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') as dateStr,
               COALESCE(SUM(p.amount), 0) as revenue,
               COUNT(p.id) as trips,
               COALESCE(AVG(p.amount), 0) as avgFare
        FROM payment_transactions p
        WHERE p.status = 'SUCCESS'
          AND p.created_at >= :startDate
          AND p.created_at <= :endDate
        GROUP BY TO_CHAR(p.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD')
        ORDER BY dateStr ASC
        """, nativeQuery = true)
    List<DailyRevenueProjection> computeDailyRevenue(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query(value = """
        SELECT p.payment_method as paymentMethod,
               COALESCE(SUM(p.amount), 0) as revenue,
               COUNT(p.id) as trips
        FROM payment_transactions p
        WHERE p.status = 'SUCCESS'
          AND p.created_at >= :startDate
          AND p.created_at <= :endDate
        GROUP BY p.payment_method
        """, nativeQuery = true)
    List<MethodRevenueProjection> computeMethodRevenue(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query(value = """
        SELECT p.status as status,
               COALESCE(SUM(p.amount), 0) as revenue,
               COUNT(p.id) as trips
        FROM payment_transactions p
        WHERE p.created_at >= :startDate
          AND p.created_at <= :endDate
        GROUP BY p.status
        """, nativeQuery = true)
    List<StatusRevenueProjection> computeStatusRevenue(
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    @Query("""
        SELECT p FROM PaymentTransaction p
        WHERE (:statusFilterActive = false OR p.status IN :statuses)
          AND (:methodFilterActive = false OR p.paymentMethod IN :methods)
          AND (p.createdAt >= :startDate)
          AND (p.createdAt <= :endDate)
        """)
    Page<PaymentTransaction> findTransactionsForAdmin(
            @Param("statuses") List<PaymentStatus> statuses,
            @Param("statusFilterActive") boolean statusFilterActive,
            @Param("methods") List<PaymentMethod> methods,
            @Param("methodFilterActive") boolean methodFilterActive,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    interface DailyRevenueProjection {
        String getDateStr();
        java.math.BigDecimal getRevenue();
        Long getTrips();
        java.math.BigDecimal getAvgFare();
    }

    interface MethodRevenueProjection {
        String getPaymentMethod();
        java.math.BigDecimal getRevenue();
        Long getTrips();
    }

    interface StatusRevenueProjection {
        String getStatus();
        java.math.BigDecimal getRevenue();
        Long getTrips();
    }
}
