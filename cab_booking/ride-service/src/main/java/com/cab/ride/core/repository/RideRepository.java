package com.cab.ride.core.repository;

import com.cab.ride.core.entity.Ride;
import com.cab.ride.core.enums.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho {@link Ride}, cung cấp CRUD và các truy vấn nghiệp vụ cơ bản.
 */
@Repository
public interface RideRepository extends JpaRepository<Ride, UUID> {

    /** Tìm tất cả cuốc xe của một khách hàng. */
    List<Ride> findAllByCustomerIdOrderByCreatedAtDesc(String customerId);

    /** Tìm tất cả cuốc xe đang được giao cho một tài xế. */
    List<Ride> findAllByDriverIdAndStatus(String driverId, RideStatus status);

    /** Tìm cuốc xe đang hoạt động (chưa kết thúc) của tài xế — phục vụ safety check. */
    Optional<Ride> findFirstByDriverIdAndStatusNotIn(String driverId, List<RideStatus> excludedStatuses);

    /**
     * Cập nhật status trực tiếp bằng JPQL — tránh tải toàn bộ entity khi chỉ cần đổi trạng thái.
     * Lưu ý: chỉ dùng khi không cần trigger @PreUpdate listener.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Ride r SET r.status = :status WHERE r.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") RideStatus status);
}
