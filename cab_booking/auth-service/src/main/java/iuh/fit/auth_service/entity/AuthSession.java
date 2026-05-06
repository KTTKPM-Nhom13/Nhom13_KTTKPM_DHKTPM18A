package iuh.fit.auth_service.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_sessions")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthSession extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    AuthUser user;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 255)
    String refreshToken;

    @Column(name = "device_id", nullable = false, length = 120)
    String deviceId;

    @Column(nullable = false, length = 30)
    String platform;

    @Column(name = "user_agent", length = 500)
    String userAgent;

    @Column(name = "app_version", length = 50)
    String appVersion;

    @Column(name = "last_used_at", nullable = false)
    LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    LocalDateTime revokedAt;

    @Column(nullable = false)
    boolean active;
}
