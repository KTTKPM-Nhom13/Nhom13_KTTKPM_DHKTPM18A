package iuh.fit.userservice.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "user_devices")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserDevice extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_profile_id", nullable = false)
    UserProfile userProfile;

    @Column(name = "device_identifier", nullable = false, length = 120)
    String deviceIdentifier;

    @Column(name = "device_name", length = 120)
    String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 20)
    DeviceType deviceType;

    @Column(length = 50)
    String platform;

    @Column(name = "os_version", length = 50)
    String osVersion;

    @Column(name = "app_version", length = 50)
    String appVersion;

    @Column(name = "push_token", length = 500)
    String pushToken;

    @Column(name = "ip_address", length = 50)
    String ipAddress;

    @Column(name = "user_agent", length = 500)
    String userAgent;

    @Column(name = "last_login_at")
    LocalDateTime lastLoginAt;

    @Column(name = "last_seen_at")
    LocalDateTime lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    DeviceSessionStatus status;

    @Column(name = "trusted_session", nullable = false)
    boolean trustedSession;
}
