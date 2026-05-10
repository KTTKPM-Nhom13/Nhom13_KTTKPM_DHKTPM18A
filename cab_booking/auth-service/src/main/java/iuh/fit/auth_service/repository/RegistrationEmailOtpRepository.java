package iuh.fit.auth_service.repository;

import iuh.fit.auth_service.entity.RegistrationEmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationEmailOtpRepository extends JpaRepository<RegistrationEmailOtp, UUID> {
    Optional<RegistrationEmailOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    List<RegistrationEmailOtp> findAllByEmailAndActiveTrue(String email);
}
