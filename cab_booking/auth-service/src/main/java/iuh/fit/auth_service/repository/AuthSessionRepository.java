package iuh.fit.auth_service.repository;

import iuh.fit.auth_service.entity.AuthSession;
import iuh.fit.auth_service.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    Optional<AuthSession> findByRefreshToken(String refreshToken);

    Optional<AuthSession> findByUserAndDeviceId(AuthUser user, String deviceId);
}
