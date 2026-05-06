package iuh.fit.userservice.service;

import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import iuh.fit.userservice.dto.request.UpsertUserProfileRequest;
import iuh.fit.userservice.dto.response.UserProfileResponse;
import iuh.fit.userservice.entity.UserProfile;
import iuh.fit.userservice.repository.UserProfileRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserProfileService {
    UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String externalUserId) {
        return toResponse(getRequiredProfile(externalUserId));
    }

    @Transactional
    public UserProfileResponse upsertProfile(String externalUserId, UpsertUserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setExternalUserId(externalUserId);
                    return created;
                });

        profile.setFullName(request.getFullName());
        profile.setEmail(request.getEmail());
        profile.setPhoneNumber(request.getPhoneNumber());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setGender(request.getGender());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setDefaultPickupNote(request.getDefaultPickupNote());

        return toResponse(userProfileRepository.save(profile));
    }

    @Transactional
    public UserProfile getOrCreateProfileEntity(String externalUserId) {
        return userProfileRepository.findByExternalUserId(externalUserId)
                .orElseGet(() -> {
                    UserProfile profile = new UserProfile();
                    profile.setExternalUserId(externalUserId);
                    profile.setFullName(externalUserId);
                    return userProfileRepository.save(profile);
                });
    }

    @Transactional(readOnly = true)
    public UserProfile getRequiredProfile(String externalUserId) {
        return userProfileRepository.findByExternalUserId(externalUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_PROFILE_NOT_FOUND));
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
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
