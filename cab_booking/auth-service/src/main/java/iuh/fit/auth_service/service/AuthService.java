package iuh.fit.auth_service.service;

import iuh.fit.auth_service.dto.request.ChangePasswordRequest;
import iuh.fit.auth_service.dto.request.InternalAccountLifecycleRequest;
import iuh.fit.auth_service.dto.request.LoginRequest;
import iuh.fit.auth_service.dto.request.LogoutRequest;
import iuh.fit.auth_service.dto.request.RefreshTokenRequest;
import iuh.fit.auth_service.dto.request.RequestForgotPasswordOtpRequest;
import iuh.fit.auth_service.dto.request.RegisterRequest;
import iuh.fit.auth_service.dto.request.RequestRegisterOtpRequest;
import iuh.fit.auth_service.dto.request.ResetForgotPasswordRequest;
import iuh.fit.auth_service.dto.request.VerifyRegisterOtpRequest;
import iuh.fit.auth_service.dto.response.AuthTokenResponse;
import iuh.fit.auth_service.dto.response.AuthUserSummaryResponse;
import iuh.fit.auth_service.dto.response.ChangePasswordResponse;
import iuh.fit.auth_service.dto.response.RegisterOtpResponse;
import iuh.fit.auth_service.dto.response.VerifyRegisterOtpResponse;
import iuh.fit.auth_service.entity.AuthProvider;
import iuh.fit.auth_service.entity.AccountLifecycleStatus;
import iuh.fit.auth_service.entity.AuthSession;
import iuh.fit.auth_service.entity.AuthUser;
import iuh.fit.auth_service.entity.PasswordResetOtp;
import iuh.fit.auth_service.entity.RegistrationEmailOtp;
import iuh.fit.auth_service.entity.UserRole;
import iuh.fit.auth_service.repository.AuthSessionRepository;
import iuh.fit.auth_service.repository.AuthUserRepository;
import iuh.fit.auth_service.repository.PasswordResetOtpRepository;
import iuh.fit.auth_service.repository.RegistrationEmailOtpRepository;
import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService {
    final AuthUserRepository authUserRepository;
    final AuthSessionRepository authSessionRepository;
    final RegistrationEmailOtpRepository registrationEmailOtpRepository;
    final PasswordResetOtpRepository passwordResetOtpRepository;
    final BCryptPasswordEncoder passwordEncoder;
    final AuthTokenService authTokenService;
    final EmailServiceClient emailServiceClient;
    final DiscoveryClient discoveryClient;

    @Value("${auth.jwt.refresh-token-days:30}")
    long refreshTokenDays;

    @Value("${auth.registration.otp-minutes:10}")
    long registrationOtpMinutes;

    @Value("${auth.registration.otp-length:6}")
    int registrationOtpLength;

    @Value("${auth.password-reset.otp-minutes:10}")
    long passwordResetOtpMinutes;

    @Value("${auth.password-reset.otp-length:6}")
    int passwordResetOtpLength;

    @Transactional
    public RegisterOtpResponse requestRegisterOtp(RequestRegisterOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        ensureEmailAvailable(email);

        LocalDateTime now = LocalDateTime.now();
        deactivatePreviousOtps(email, now);

        String otpCode = generateOtpCode();
        RegistrationEmailOtp otp = new RegistrationEmailOtp();
        otp.setEmail(email);
        otp.setOtpHash(passwordEncoder.encode(otpCode));
        otp.setExpiresAt(now.plusMinutes(registrationOtpMinutes));
        otp.setActive(true);

        registrationEmailOtpRepository.save(otp);

        try {
            emailServiceClient.sendRegistrationOtpEmail(email, otpCode, registrationOtpMinutes);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, ex);
        }

        return RegisterOtpResponse.builder()
                .email(email)
                .expiresInSeconds(registrationOtpMinutes * 60)
                .build();
    }

    @Transactional
    public VerifyRegisterOtpResponse verifyRegisterOtp(VerifyRegisterOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        ensureEmailAvailable(email);

        RegistrationEmailOtp otp = getLatestOtp(email);
        validateOtpForVerification(otp, request.getOtpCode());

        if (otp.getVerifiedAt() == null) {
            otp.setVerifiedAt(LocalDateTime.now());
        }
        registrationEmailOtpRepository.save(otp);

        return VerifyRegisterOtpResponse.builder()
                .email(email)
                .verified(true)
                .canRegister(true)
                .verifiedAt(otp.getVerifiedAt())
                .build();
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        ensureEmailAvailable(email);

        AuthUser user = new AuthUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAvatarUrl(resolveAvatar(request.getAvatarUrl(), request.getFullName()));
        user.setRole(parseRequestedRole(request.getRole()));
        user.setProvider(AuthProvider.LOCAL_EMAIL);
        user.setEmailVerified(true);
        user.setActive(true);
        user.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        user.setLastLoginAt(LocalDateTime.now());

        AuthUser savedUser = authUserRepository.save(user);

        // Sync profile immediately to the respective domain microservice (user-service or driver-service)
        syncProfileToDomainService(savedUser);

        AuthTokenResponse response = createSessionResponse(savedUser, request.getDeviceId(), request.getPlatform(),
                request.getUserAgent(), request.getAppVersion());
        return response;
    }

    private void syncProfileToDomainService(AuthUser user) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", user.getId().toString());
            payload.put("fullName", user.getFullName());
            payload.put("email", user.getEmail());
            payload.put("phoneNumber", user.getPhoneNumber());
            payload.put("avatarUrl", user.getAvatarUrl());

            if (user.getRole() == UserRole.USER) {
                String baseUrl = getServiceUrl("user-service", "http://user-service:8082");
                restTemplate.postForObject(baseUrl + "/internal/users", payload, Object.class);
            } else if (user.getRole() == UserRole.DRIVER) {
                String baseUrl = getServiceUrl("driver-service", "http://driver-service:8083");
                restTemplate.postForObject(baseUrl + "/internal/drivers", payload, Object.class);
            }
        } catch (Exception e) {
            System.err.println("Failed to sync profile immediately on registration: " + e.getMessage());
        }
    }

    private String getServiceUrl(String serviceName, String defaultFallback) {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
            if (instances != null && !instances.isEmpty()) {
                return instances.get(0).getUri().toString();
            }
        } catch (Exception e) {
            System.err.println("Failed to lookup service via Eureka: " + e.getMessage());
        }
        return defaultFallback;
    }

    @Transactional
    public AuthTokenResponse registerAdmin(RegisterRequest request) {
        AuthUser currentUser = getAuthenticatedUser();
        if (currentUser.getRole() != UserRole.ADMIN) {
            throw new AppException(ErrorCode.UNAUTHORIZED_ACCESS);
        }

        String email = normalizeEmail(request.getEmail());
        ensureEmailAvailable(email);

        AuthUser user = new AuthUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAvatarUrl(resolveAvatar(request.getAvatarUrl(), request.getFullName()));
        user.setRole(UserRole.ADMIN);
        user.setProvider(AuthProvider.LOCAL_EMAIL);
        user.setEmailVerified(true);
        user.setActive(true);
        user.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        user.setLastLoginAt(LocalDateTime.now());

        AuthUser savedUser = authUserRepository.save(user);
        return createSessionResponse(savedUser, request.getDeviceId(), request.getPlatform(),
                request.getUserAgent(), request.getAppVersion());
    }


    /*
    Old OTP-guarded register flow kept for reference:
    RegistrationEmailOtp verifiedOtp = getVerifiedOtpForRegistration(email);
    ...
    consumeOtp(verifiedOtp);
    emailServiceClient.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName());
    */

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        ensureLoginAllowed(user);
        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setLastLoginAt(LocalDateTime.now());
        authUserRepository.save(user);

        return createSessionResponse(user, request.getDeviceId(), request.getPlatform(),
                request.getUserAgent(), request.getAppVersion());
    }

    @Transactional
    public ChangePasswordResponse changePassword(ChangePasswordRequest request) {
        AuthUser user = getAuthenticatedUser();

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setLastLoginAt(LocalDateTime.now());
        authUserRepository.save(user);
        revokeAllSessions(user);

        return ChangePasswordResponse.builder()
                .email(user.getEmail())
                .changedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public RegisterOtpResponse requestForgotPasswordOtp(RequestForgotPasswordOtpRequest request) {
        String email = normalizeEmail(request.getEmail());
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        deactivatePreviousPasswordResetOtps(email, now);

        String otpCode = generateOtpCode(passwordResetOtpLength);
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setEmail(user.getEmail());
        otp.setOtpHash(passwordEncoder.encode(otpCode));
        otp.setExpiresAt(now.plusMinutes(passwordResetOtpMinutes));
        otp.setActive(true);
        passwordResetOtpRepository.save(otp);

        try {
            emailServiceClient.sendForgotPasswordOtpEmail(user.getEmail(), otpCode, passwordResetOtpMinutes);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED, ex);
        }

        return RegisterOtpResponse.builder()
                .email(user.getEmail())
                .expiresInSeconds(passwordResetOtpMinutes * 60)
                .build();
    }

    @Transactional
    public ChangePasswordResponse resetForgotPassword(ResetForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_USER_NOT_FOUND));

        PasswordResetOtp otp = passwordResetOtpRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_OTP_INVALID));
        validatePasswordResetOtp(otp, request.getOtpCode());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setLastLoginAt(LocalDateTime.now());
        authUserRepository.save(user);

        otp.setActive(false);
        otp.setConsumedAt(LocalDateTime.now());
        passwordResetOtpRepository.save(otp);
        revokeAllSessions(user);

        return ChangePasswordResponse.builder()
                .email(user.getEmail())
                .changedAt(LocalDateTime.now())
                .build();
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        AuthSession session = authSessionRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (!session.isActive() || session.getRevokedAt() != null) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        ensureRefreshAllowed(session.getUser());

        session.setRefreshToken(authTokenService.generateRefreshToken());
        session.setLastUsedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));

        AuthSession savedSession = authSessionRepository.save(session);
        return buildAuthResponse(savedSession.getUser(), savedSession);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        AuthSession session = authSessionRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_SESSION_NOT_FOUND));

        session.setActive(false);
        session.setRevokedAt(LocalDateTime.now());
        session.setLastUsedAt(LocalDateTime.now());
        authSessionRepository.save(session);
    }

    @Transactional
    public String generateDebugToken() {
        String email = "tai.demo@cab.local";
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            AuthUser demo = new AuthUser();
            demo.setEmail(email);
            demo.setPasswordHash(passwordEncoder.encode("123456"));
            demo.setFullName("Tai Demo");
            demo.setAvatarUrl("https://ui-avatars.com/api/?name=Tai+Demo");
            demo.setRole(UserRole.USER);
            demo.setProvider(AuthProvider.LOCAL_EMAIL);
            demo.setEmailVerified(true);
            demo.setActive(true);
            demo.setAccountStatus(AccountLifecycleStatus.ACTIVE);
            demo.setLastLoginAt(LocalDateTime.now());
            return authUserRepository.save(demo);
        });
        return authTokenService.generateAccessToken(user, "debug-device", "WEB");
    }

    @Transactional
    public void syncAccountLifecycle(UUID userId, InternalAccountLifecycleRequest request) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_USER_NOT_FOUND));

        AccountLifecycleStatus status = parseAccountStatus(request.getAccountStatus());
        user.setAccountStatus(status);
        user.setDeletionRequestedAt(request.getDeletionRequestedAt());
        user.setScheduledDeletionAt(request.getScheduledDeletionAt());
        user.setDeletionReason(request.getDeletionReason());
        user.setActive(status == AccountLifecycleStatus.ACTIVE);
        authUserRepository.save(user);
    }

    private AuthTokenResponse createSessionResponse(AuthUser user, String deviceId, String platform,
                                                    String userAgent, String appVersion) {
        AuthSession session = authSessionRepository.findByUserAndDeviceId(user, deviceId)
                .orElseGet(AuthSession::new);

        session.setUser(user);
        session.setDeviceId(deviceId);
        session.setPlatform(platform);
        session.setUserAgent(userAgent);
        session.setAppVersion(appVersion);
        session.setRefreshToken(authTokenService.generateRefreshToken());
        session.setLastUsedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        session.setRevokedAt(null);
        session.setActive(true);

        return buildAuthResponse(user, authSessionRepository.save(session));
    }

    private AuthTokenResponse buildAuthResponse(AuthUser user, AuthSession session) {
        return AuthTokenResponse.builder()
                .accessToken(authTokenService.generateAccessToken(user, session.getDeviceId(), session.getPlatform()))
                .refreshToken(session.getRefreshToken())
                .tokenType("Bearer")
                .expiresInSeconds(authTokenService.getAccessTokenExpiresInSeconds())
                .deviceId(session.getDeviceId())
                .platform(session.getPlatform())
                .user(AuthUserSummaryResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl() == null ? "" : user.getAvatarUrl())
                        .phoneNumber(user.getPhoneNumber())
                        .role(user.getRole().name())
                        .emailVerified(user.isEmailVerified())
                        .accountStatus(resolveAccountStatus(user).name())
                        .scheduledDeletionAt(user.getScheduledDeletionAt())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void ensureEmailAvailable(String email) {
        if (authUserRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void ensureLoginAllowed(AuthUser user) {
        AccountLifecycleStatus status = resolveAccountStatus(user);
        if (status == AccountLifecycleStatus.PENDING_DELETION) {
            throw new AppException(ErrorCode.ACCOUNT_PENDING_DELETION);
        }
        if (status == AccountLifecycleStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
        }
    }

    private void ensureRefreshAllowed(AuthUser user) {
        AccountLifecycleStatus status = resolveAccountStatus(user);
        if (status == AccountLifecycleStatus.DELETED) {
            throw new AppException(ErrorCode.ACCOUNT_RESTORE_WINDOW_EXPIRED);
        }
    }

    private RegistrationEmailOtp getLatestOtp(String email) {
        return registrationEmailOtpRepository.findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new AppException(ErrorCode.REGISTRATION_OTP_INVALID));
    }

    private void validateOtpForVerification(RegistrationEmailOtp otp, String otpCode) {
        if (!otp.isActive() || otp.getConsumedAt() != null) {
            throw new AppException(ErrorCode.REGISTRATION_OTP_INVALID);
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setActive(false);
            registrationEmailOtpRepository.save(otp);
            throw new AppException(ErrorCode.REGISTRATION_OTP_EXPIRED);
        }
        if (!passwordEncoder.matches(otpCode, otp.getOtpHash())) {
            throw new AppException(ErrorCode.REGISTRATION_OTP_INVALID);
        }
    }

    private RegistrationEmailOtp getVerifiedOtpForRegistration(String email) {
        RegistrationEmailOtp otp = getLatestOtp(email);
        if (!otp.isActive() || otp.getConsumedAt() != null || otp.getVerifiedAt() == null) {
            throw new AppException(ErrorCode.REGISTRATION_EMAIL_NOT_VERIFIED);
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setActive(false);
            registrationEmailOtpRepository.save(otp);
            throw new AppException(ErrorCode.REGISTRATION_OTP_EXPIRED);
        }
        return otp;
    }

    private void deactivatePreviousOtps(String email, LocalDateTime now) {
        List<RegistrationEmailOtp> existingOtps = registrationEmailOtpRepository.findAllByEmailAndActiveTrue(email);
        for (RegistrationEmailOtp existingOtp : existingOtps) {
            existingOtp.setActive(false);
            if (existingOtp.getConsumedAt() == null) {
                existingOtp.setConsumedAt(now);
            }
        }
        if (!existingOtps.isEmpty()) {
            registrationEmailOtpRepository.saveAll(existingOtps);
        }
    }

    private void consumeOtp(RegistrationEmailOtp otp) {
        otp.setActive(false);
        otp.setConsumedAt(LocalDateTime.now());
        registrationEmailOtpRepository.save(otp);
    }

    private String generateOtpCode() {
        return generateOtpCode(registrationOtpLength);
    }

    private String generateOtpCode(int desiredLength) {
        int effectiveLength = Math.max(4, desiredLength);
        int bound = (int) Math.pow(10, effectiveLength);
        int floor = (int) Math.pow(10, effectiveLength - 1);
        return String.valueOf(ThreadLocalRandom.current().nextInt(floor, bound));
    }

    private void deactivatePreviousPasswordResetOtps(String email, LocalDateTime now) {
        List<PasswordResetOtp> existingOtps = passwordResetOtpRepository.findAllByEmailAndActiveTrue(email);
        for (PasswordResetOtp existingOtp : existingOtps) {
            existingOtp.setActive(false);
            if (existingOtp.getConsumedAt() == null) {
                existingOtp.setConsumedAt(now);
            }
        }
        if (!existingOtps.isEmpty()) {
            passwordResetOtpRepository.saveAll(existingOtps);
        }
    }

    private void validatePasswordResetOtp(PasswordResetOtp otp, String otpCode) {
        if (!otp.isActive() || otp.getConsumedAt() != null) {
            throw new AppException(ErrorCode.REGISTRATION_OTP_INVALID);
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setActive(false);
            passwordResetOtpRepository.save(otp);
            throw new AppException(ErrorCode.REGISTRATION_OTP_EXPIRED);
        }
        if (!passwordEncoder.matches(otpCode, otp.getOtpHash())) {
            throw new AppException(ErrorCode.REGISTRATION_OTP_INVALID);
        }
    }

    private AuthUser getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AppException(ErrorCode.AUTH_USER_NOT_FOUND);
        }

        UUID userId = UUID.fromString(authentication.getName());
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.AUTH_USER_NOT_FOUND));
        resolveAccountStatus(user);
        return user;
    }

    private void revokeAllSessions(AuthUser user) {
        authSessionRepository.findAllByUser(user).forEach(session -> {
            session.setActive(false);
            session.setRevokedAt(LocalDateTime.now());
            session.setLastUsedAt(LocalDateTime.now());
        });
    }

    private String resolveAvatar(String avatarUrl, String fullName) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            return avatarUrl;
        }
        return "https://ui-avatars.com/api/?name=" + fullName.trim().replace(" ", "+");
    }

    private AccountLifecycleStatus resolveAccountStatus(AuthUser user) {
        if (user.getAccountStatus() == null) {
            user.setAccountStatus(AccountLifecycleStatus.ACTIVE);
        }
        if (user.getAccountStatus() == AccountLifecycleStatus.PENDING_DELETION
                && user.getScheduledDeletionAt() != null
                && user.getScheduledDeletionAt().isBefore(LocalDateTime.now())) {
            user.setAccountStatus(AccountLifecycleStatus.DELETED);
            user.setActive(false);
            authUserRepository.save(user);
        }
        return user.getAccountStatus();
    }

    private AccountLifecycleStatus parseAccountStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return AccountLifecycleStatus.ACTIVE;
        }
        return AccountLifecycleStatus.valueOf(rawStatus.trim().toUpperCase());
    }

    private UserRole parseRequestedRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return UserRole.USER;
        }
        UserRole requestedRole = UserRole.valueOf(rawRole.trim().toUpperCase());
        if (requestedRole == UserRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        return requestedRole;
    }
}
