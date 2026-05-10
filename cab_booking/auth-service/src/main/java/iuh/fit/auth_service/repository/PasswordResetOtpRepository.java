package iuh.fit.auth_service.repository;

import iuh.fit.auth_service.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, UUID> {
    Optional<PasswordResetOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    List<PasswordResetOtp> findAllByEmailAndActiveTrue(String email);

    void deleteAllByExpiresAtBefore(LocalDateTime cutoff);
}
