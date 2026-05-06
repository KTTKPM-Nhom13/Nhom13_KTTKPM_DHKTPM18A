package iuh.fit.auth_service.repository;

import iuh.fit.auth_service.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
    Optional<AuthUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
