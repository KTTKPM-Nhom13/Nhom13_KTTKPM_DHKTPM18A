package iuh.fit.userservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.userservice.dto.request.RequestAccountDeletionRequest;
import iuh.fit.userservice.dto.request.UpsertUserProfileRequest;
import iuh.fit.userservice.dto.response.UserAccountResponse;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.entity.AccountLifecycleStatus;
import iuh.fit.userservice.entity.UserProfile;
import iuh.fit.userservice.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {
    UserProfileRepository userProfileRepository;
    AuthAccountSyncClient authAccountSyncClient;
    static final long ACCOUNT_RESTORE_WINDOW_DAYS = 30;

    @Transactional
    public UserProfileResponse getProfile(String externalUserId) {
        return toResponse(getOrCreateProfileEntity(externalUserId));
    }

    @Transactional
    public UserProfileResponse upsertProfile(String externalUserId, UpsertUserProfileRequest request) {
        UserProfile profile = getOrCreateProfileEntity(externalUserId);
        ensureProfileWritable(profile);

        profile.setFullName(request.getFullName());
        if (hasText(request.getEmail())) {
            profile.setEmail(request.getEmail().trim());
        }
        if (hasText(request.getPhoneNumber())) {
            profile.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (hasText(request.getAvatarUrl())) {
            profile.setAvatarUrl(request.getAvatarUrl().trim());
        }
        if (hasText(request.getGender())) {
            profile.setGender(request.getGender().trim().toUpperCase());
        }
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getDefaultPickupNote() != null) {
            profile.setDefaultPickupNote(request.getDefaultPickupNote().trim());
        }

        return toResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public UserAccountResponse getAccount(String externalUserId) {
        UserProfile profile = getOrCreateProfileEntity(externalUserId);
        return toAccountResponse(profile);
    }

    @Transactional
    public UserAccountResponse requestAccountDeletion(String externalUserId, RequestAccountDeletionRequest request) {
        UserProfile profile = getOrCreateProfileEntity(externalUserId);
        if (profile.getAccountStatus() == AccountLifecycleStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
        }
        if (profile.getAccountStatus() != AccountLifecycleStatus.PENDING_DELETION) {
            LocalDateTime now = LocalDateTime.now();
            profile.setAccountStatus(AccountLifecycleStatus.PENDING_DELETION);
            profile.setDeletionRequestedAt(now);
            profile.setScheduledDeletionAt(now.plusDays(ACCOUNT_RESTORE_WINDOW_DAYS));
            profile.setDeletionReason(request.getReason());
            userProfileRepository.save(profile);
            authAccountSyncClient.syncAccountLifecycle(profile);
        }
        return toAccountResponse(profile);
    }

    @Transactional
    public UserAccountResponse restoreAccount(String externalUserId) {
        UserProfile profile = getOrCreateProfileEntity(externalUserId);
        if (profile.getAccountStatus() == AccountLifecycleStatus.ACTIVE) {
            return toAccountResponse(profile);
        }
        if (profile.getAccountStatus() == AccountLifecycleStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
        }

        profile.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        profile.setDeletionRequestedAt(null);
        profile.setScheduledDeletionAt(null);
        profile.setDeletionReason(null);
        userProfileRepository.save(profile);
        authAccountSyncClient.syncAccountLifecycle(profile);
        return toAccountResponse(profile);
    }

    @Transactional
    public UserProfile getOrCreateProfileEntity(String externalUserId) {
        UserProfile profile = userProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setExternalUserId(externalUserId);
                    created.setFullName(resolveCurrentUserClaim("fullName", externalUserId));
                    created.setEmail(resolveCurrentUserClaim("email", null));
                    created.setPhoneNumber(resolveCurrentUserClaim("phoneNumber", null));
                    created.setAvatarUrl(resolveCurrentUserClaim("avatarUrl", null));
                    created.setAccountStatus(AccountLifecycleStatus.ACTIVE);
                    return userProfileRepository.save(created);
                });
        syncMissingAuthSnapshot(profile);
        return transitionDeletedIfExpired(profile);
    }

    @Transactional
    public UserProfile getRequiredProfile(String externalUserId) {
        UserProfile profile = userProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
        return transitionDeletedIfExpired(profile);
    }

    @Transactional
    public UserProfile getWritableProfileEntity(String externalUserId) {
        UserProfile profile = getOrCreateProfileEntity(externalUserId);
        ensureProfileWritable(profile);
        return profile;
    }

    private void ensureProfileWritable(UserProfile profile) {
        UserProfile resolvedProfile = transitionDeletedIfExpired(profile);
        if (resolvedProfile.getAccountStatus() == AccountLifecycleStatus.PENDING_DELETION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_DELETION);
        }
        if (resolvedProfile.getAccountStatus() == AccountLifecycleStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
        }
    }

    private UserProfile transitionDeletedIfExpired(UserProfile profile) {
        if (profile.getAccountStatus() == AccountLifecycleStatus.PENDING_DELETION
                && profile.getScheduledDeletionAt() != null
                && profile.getScheduledDeletionAt().isBefore(LocalDateTime.now())) {
            profile.setAccountStatus(AccountLifecycleStatus.DELETED);
            userProfileRepository.save(profile);
            authAccountSyncClient.syncAccountLifecycle(profile);
        }
        return profile;
    }

    private void syncMissingAuthSnapshot(UserProfile profile) {
        boolean dirty = false;

        if (!hasText(profile.getFullName())) {
            profile.setFullName(resolveCurrentUserClaim("fullName", profile.getExternalUserId()));
            dirty = true;
        }
        if (!hasText(profile.getEmail())) {
            String email = resolveCurrentUserClaim("email", null);
            if (hasText(email)) {
                profile.setEmail(email);
                dirty = true;
            }
        }
        if (!hasText(profile.getPhoneNumber())) {
            String phoneNumber = normalizeNullableClaim(resolveCurrentUserClaim("phoneNumber", null));
            if (hasText(phoneNumber)) {
                profile.setPhoneNumber(phoneNumber);
                dirty = true;
            }
        }
        if (!hasText(profile.getAvatarUrl())) {
            String avatarUrl = normalizeNullableClaim(resolveCurrentUserClaim("avatarUrl", null));
            if (hasText(avatarUrl)) {
                profile.setAvatarUrl(avatarUrl);
                dirty = true;
            }
        }

        if (dirty) {
            userProfileRepository.save(profile);
        }
    }

    private String resolveCurrentUserClaim(String claimName, String fallbackValue) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return normalizeNullableClaim(jwt.getClaimAsString(claimName), fallbackValue);
        }
        return fallbackValue;
    }

    private String normalizeNullableClaim(String value) {
        return normalizeNullableClaim(value, null);
    }

    private String normalizeNullableClaim(String value, String fallbackValue) {
        if (!hasText(value)) {
            return fallbackValue;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private UserProfileResponse toResponse(UserProfile profile) {
        return UserProfileResponse.builder()
                .id(profile.getId())
                .externalUserId(profile.getExternalUserId())
                .fullName(profile.getFullName())
                .email(profile.getEmail())
                .phoneNumber(profile.getPhoneNumber())
                .avatarUrl(profile.getAvatarUrl())
                .gender(profile.getGender())
                .dateOfBirth(profile.getDateOfBirth())
                .defaultPickupNote(profile.getDefaultPickupNote())
                .accountStatus(profile.getAccountStatus().name())
                .deletionRequestedAt(profile.getDeletionRequestedAt())
                .scheduledDeletionAt(profile.getScheduledDeletionAt())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private UserAccountResponse toAccountResponse(UserProfile profile) {
        UserProfile resolvedProfile = transitionDeletedIfExpired(profile);
        return UserAccountResponse.builder()
                .profileId(resolvedProfile.getId())
                .externalUserId(resolvedProfile.getExternalUserId())
                .fullName(resolvedProfile.getFullName())
                .email(resolvedProfile.getEmail())
                .phoneNumber(resolvedProfile.getPhoneNumber())
                .avatarUrl(resolvedProfile.getAvatarUrl())
                .accountStatus(resolvedProfile.getAccountStatus().name())
                .deletionRequestedAt(resolvedProfile.getDeletionRequestedAt())
                .scheduledDeletionAt(resolvedProfile.getScheduledDeletionAt())
                .restoreEligible(resolvedProfile.getAccountStatus() == AccountLifecycleStatus.PENDING_DELETION
                        && resolvedProfile.getScheduledDeletionAt() != null
                        && !resolvedProfile.getScheduledDeletionAt().isBefore(LocalDateTime.now()))
                .createdAt(resolvedProfile.getCreatedAt())
                .updatedAt(resolvedProfile.getUpdatedAt())
                .build();
    }
}
