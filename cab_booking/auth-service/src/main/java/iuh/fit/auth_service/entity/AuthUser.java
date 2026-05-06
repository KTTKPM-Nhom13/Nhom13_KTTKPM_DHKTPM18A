package iuh.fit.auth_service.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_users")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthUser extends BaseEntity {
    @Column(nullable = false, unique = true, length = 150)
    String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    String fullName;

    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(name = "phone_number", length = 20)
    String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    AuthProvider provider;

    @Column(name = "email_verified", nullable = false)
    boolean emailVerified;

    @Column(nullable = false)
    boolean active;

    @Column(name = "last_login_at")
    LocalDateTime lastLoginAt;
}
