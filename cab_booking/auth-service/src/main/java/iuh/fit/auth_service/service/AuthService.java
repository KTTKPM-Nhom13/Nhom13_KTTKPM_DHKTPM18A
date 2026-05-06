package iuh.fit.auth_service.service;

import iuh.fit.auth_service.dto.request.LoginRequest;
import iuh.fit.auth_service.dto.request.LogoutRequest;
import iuh.fit.auth_service.dto.request.RefreshTokenRequest;
import iuh.fit.auth_service.dto.request.RegisterRequest;
import iuh.fit.auth_service.dto.response.AuthTokenResponse;
import iuh.fit.auth_service.dto.response.AuthUserSummaryResponse;
import iuh.fit.auth_service.entity.AuthProvider;
import iuh.fit.auth_service.entity.AuthSession;
import iuh.fit.auth_service.entity.AuthUser;
import iuh.fit.auth_service.entity.UserRole;
import iuh.fit.auth_service.repository.AuthSessionRepository;
import iuh.fit.auth_service.repository.AuthUserRepository;
import iuh.fit.common.exception.AppException;
import iuh.fit.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthService {
    final AuthUserRepository authUserRepository;
    final AuthSessionRepository authSessionRepository;
    final BCryptPasswordEncoder passwordEncoder;
    final AuthTokenService authTokenService;
    final EmailServiceClient emailServiceClient;

    @Value("${auth.jwt.refresh-token-days:30}")
    long refreshTokenDays;

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (authUserRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        AuthUser user = new AuthUser();
        user.setEmail(normalizeEmail(request.getEmail()));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAvatarUrl(resolveAvatar(request.getAvatarUrl(), request.getFullName()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL_EMAIL);
        user.setEmailVerified(false);
        user.setActive(true);
        user.setLastLoginAt(LocalDateTime.now());

        AuthUser savedUser = authUserRepository.save(user);
        AuthTokenResponse response = createSessionResponse(savedUser, request.getDeviceId(), request.getPlatform(),
                request.getUserAgent(), request.getAppVersion());
        emailServiceClient.sendWelcomeEmail(savedUser.getEmail(), savedUser.getFullName());
        return response;
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

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
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        AuthSession session = authSessionRepository.findByRefreshToken(request.getRefreshToken())
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (!session.isActive() || session.getRevokedAt() != null) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

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
            demo.setLastLoginAt(LocalDateTime.now());
            return authUserRepository.save(demo);
        });
        return authTokenService.generateAccessToken(user, "debug-device", "WEB");
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
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String resolveAvatar(String avatarUrl, String fullName) {
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            return avatarUrl;
        }
        return "https://ui-avatars.com/api/?name=" + fullName.trim().replace(" ", "+");
    }
}
