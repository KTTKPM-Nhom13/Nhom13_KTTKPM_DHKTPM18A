package iuh.fit.auth_service.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_email_otps")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegistrationEmailOtp extends BaseEntity {
    @Column(nullable = false, length = 150)
    String email;

    @Column(name = "otp_hash", nullable = false, length = 255)
    String otpHash;

    @Column(name = "expires_at", nullable = false)
    LocalDateTime expiresAt;

    @Column(name = "verified_at")
    LocalDateTime verifiedAt;

    @Column(name = "consumed_at")
    LocalDateTime consumedAt;

    @Column(nullable = false)
    boolean active;
}
