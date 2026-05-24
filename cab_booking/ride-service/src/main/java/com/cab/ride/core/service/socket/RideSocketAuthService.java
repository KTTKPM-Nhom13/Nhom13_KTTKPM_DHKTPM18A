package com.cab.ride.core.service.socket;

import com.corundumstudio.socketio.SocketIOClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Authenticates Socket.IO clients using JWT.
 *
 * <p>Token extraction order (Socket.IO v4 compatible):
 * <ol>
 *   <li>{@code auth.token} — Socket.IO v4 handshake auth object via {@code getAuthToken()}</li>
 *   <li>{@code ?token=<JWT>} — query parameter (backward compatible with v2/v3 clients)</li>
 *   <li>{@code Authorization: Bearer <JWT>} — HTTP header fallback</li>
 * </ol>
 *
 * <p>Role extraction checks these JWT claims (first non-empty wins):<br>
 * {@code role}, {@code roles}, {@code scope}, {@code scp}, {@code authorities}
 *
 * <p>Accepts String, Collection, or space-delimited values.
 * Normalizes by stripping {@code ROLE_}/{@code SCOPE_} prefixes and uppercasing.
 *
 * <p>On successful authentication, the client's {@code userId} and {@code roles} are stored
 * as client attributes for downstream event handlers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RideSocketAuthService {

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_ROLES = "roles";

    /** Claim names to check for roles, in priority order. */
    private static final List<String> ROLE_CLAIM_NAMES = List.of("role", "roles", "scope", "scp", "authorities");

    /** Prefixes to strip when normalizing role values. */
    private static final List<String> ROLE_PREFIXES = List.of("ROLE_", "SCOPE_");

    private final JwtDecoder jwtDecoder;

    /**
     * Attempts to authenticate the client from the handshake data.
     *
     * @return true if authentication succeeded, false otherwise
     */
    public boolean authenticate(SocketIOClient client) {
        String token = extractToken(client);
        if (token == null || token.isBlank()) {
            log.warn("[RideSocket] Auth failed: no token found (checked auth.token, query.token, Authorization header), sessionId={}", client.getSessionId());
            return false;
        }

        try {
            Jwt jwt = jwtDecoder.decode(token);
            String userId = jwt.getSubject();
            if (userId == null || userId.isBlank()) {
                log.warn("[RideSocket] Auth failed: JWT has no subject, sessionId={}", client.getSessionId());
                return false;
            }

            // Extract and normalize roles from JWT
            Set<String> roles = extractRoles(jwt);

            client.set(ATTR_USER_ID, userId);
            client.set(ATTR_ROLES, new ArrayList<>(roles));

            // TODO remove debug logs after production validation
            log.info("[SOCKET_AUTH] sub={} roleClaim={} scopeClaim={} normalizedRoles={}",
                    userId,
                    jwt.getClaimAsString("role"),
                    jwt.getClaimAsString("scope"),
                    roles);

            log.info("[RideSocket] Authenticated: userId={} roles={} sessionId={}", userId, roles, client.getSessionId());
            return true;
        } catch (Exception ex) {
            log.warn("[RideSocket] Auth failed: invalid JWT, sessionId={} error={}", client.getSessionId(), ex.getMessage());
            return false;
        }
    }

    /**
     * Extracts roles from JWT by checking multiple common claim names.
     *
     * <p>Checks claims in priority order: {@code role}, {@code roles}, {@code scope}, {@code scp}, {@code authorities}.
     * The first claim that yields a non-empty set of roles wins.
     *
     * <p>Accepts:
     * <ul>
     *   <li>String value: {@code "DRIVER"} or {@code "DRIVER ADMIN"} (space-delimited)</li>
     *   <li>Collection: {@code ["DRIVER", "ADMIN"]}</li>
     * </ul>
     *
     * <p>Normalizes by stripping {@code ROLE_}/{@code SCOPE_} prefixes and uppercasing.
     * <p>Example: {@code "ROLE_DRIVER"} → {@code "DRIVER"}, {@code "SCOPE_ADMIN"} → {@code "ADMIN"}
     */
    private Set<String> extractRoles(Jwt jwt) {
        for (String claimName : ROLE_CLAIM_NAMES) {
            // Try as collection first (e.g. "roles": ["DRIVER", "ADMIN"])
            List<String> listValue = jwt.getClaimAsStringList(claimName);
            if (listValue != null && !listValue.isEmpty()) {
                Set<String> normalized = normalizeRoles(listValue);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }

            // Try as string (e.g. "role": "DRIVER" or "scope": "DRIVER ADMIN")
            String stringValue = jwt.getClaimAsString(claimName);
            if (stringValue != null && !stringValue.isBlank()) {
                Set<String> normalized = normalizeRoles(Arrays.asList(stringValue.split("\\s+")));
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        return Collections.emptySet();
    }

    /**
     * Normalizes role strings: trims, strips {@code ROLE_}/{@code SCOPE_} prefixes, uppercases, deduplicates.
     */
    private Set<String> normalizeRoles(Collection<String> rawRoles) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : rawRoles) {
            if (raw == null || raw.isBlank()) continue;
            String normalized = raw.trim().toUpperCase();
            for (String prefix : ROLE_PREFIXES) {
                if (normalized.startsWith(prefix)) {
                    normalized = normalized.substring(prefix.length());
                    break;
                }
            }
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    public Optional<String> getUserId(SocketIOClient client) {
        return Optional.ofNullable(client.get(ATTR_USER_ID));
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(SocketIOClient client) {
        List<String> roles = client.get(ATTR_ROLES);
        return roles != null ? roles : List.of();
    }

    /**
     * Checks if the client has the given role.
     * Roles are stored normalized (e.g. "DRIVER"), so direct comparison is sufficient.
     */
    public boolean hasRole(SocketIOClient client, String role) {
        String normalizedTarget = role.trim().toUpperCase();
        for (String prefix : ROLE_PREFIXES) {
            if (normalizedTarget.startsWith(prefix)) {
                normalizedTarget = normalizedTarget.substring(prefix.length());
                break;
            }
        }
        final String target = normalizedTarget;
        return getRoles(client).stream().anyMatch(r -> r.equalsIgnoreCase(target));
    }

    /**
     * Extracts JWT token from handshake data with multiple fallback strategies.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Socket.IO v4 {@code auth.token} — sent as handshake auth object via {@code getAuthToken()}</li>
     *   <li>Query parameter {@code ?token=<JWT>} — backward compatible</li>
     *   <li>HTTP header {@code Authorization: Bearer <JWT>}</li>
     * </ol>
     */
    @SuppressWarnings("unchecked")
    private String extractToken(SocketIOClient client) {
        // 1. Try auth token (Socket.IO v4 style: io(url, { auth: { token: jwt } }))
        //    netty-socketio stores the auth object from the handshake via getAuthToken()
        try {
            Object authToken = client.getHandshakeData().getAuthToken();
            if (authToken instanceof Map) {
                Object token = ((Map<String, Object>) authToken).get("token");
                if (token != null && !token.toString().isBlank()) {
                    log.debug("[RideSocket] Token extracted from auth.token (Socket.IO v4 style)");
                    return token.toString();
                }
            } else if (authToken instanceof String && !((String) authToken).isBlank()) {
                // Auth token might be passed directly as a string
                log.debug("[RideSocket] Token extracted from auth token string (Socket.IO v4 style)");
                return (String) authToken;
            }
        } catch (Exception ex) {
            log.debug("[RideSocket] Could not read auth token: {}", ex.getMessage());
        }

        // 2. Try query param: ?token=<JWT>
        List<String> tokenParam = client.getHandshakeData().getUrlParams().get("token");
        if (tokenParam != null && !tokenParam.isEmpty() && !tokenParam.get(0).isBlank()) {
            log.debug("[RideSocket] Token extracted from query parameter (backward compatible)");
            return tokenParam.get(0);
        }

        // 3. Fallback: Authorization header
        String authHeader = client.getHandshakeData().getHttpHeaders().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("[RideSocket] Token extracted from Authorization header");
            return authHeader.substring(7);
        }

        return null;
    }
}
