package iuh.fit.userservice.entity;

import iuh.fit.common.model.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfile extends BaseEntity {

    @Column(name = "external_user_id", nullable = false, unique = true, length = 100)
    String externalUserId;

    @Column(name = "full_name", length = 150)
    String fullName;

    @Column(length = 150)
    String email;

    @Column(name = "phone_number", length = 20)
    String phoneNumber;

    @Column(name = "avatar_url", length = 500)
    String avatarUrl;

    @Column(length = 20)
    String gender;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Column(name = "default_pickup_note", length = 255)
    String defaultPickupNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    AccountLifecycleStatus accountStatus = AccountLifecycleStatus.ACTIVE;

    @Column(name = "deletion_requested_at")
    LocalDateTime deletionRequestedAt;

    @Column(name = "scheduled_deletion_at")
    LocalDateTime scheduledDeletionAt;

    @Column(name = "deletion_reason", length = 255)
    String deletionReason;

    @OneToMany(mappedBy = "userProfile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    List<UserDevice> devices = new ArrayList<>();

    public String getUserId() {
        return getId() != null ? getId().toString() : null;
    }

    public boolean isEmailVerified() {
        return true;
    }

    public LocalDateTime getLastLoginAt() {
        return getUpdatedAt() != null ? getUpdatedAt() : LocalDateTime.now();
    }
}
