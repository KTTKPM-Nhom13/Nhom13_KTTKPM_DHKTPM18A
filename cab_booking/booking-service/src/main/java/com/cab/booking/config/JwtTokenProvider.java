package com.cab.booking.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// @Component  // ❌ TẠM VÔ HIỆU HÓA — Auth Service chưa hoàn thiện, không có public_key.pem
public class JwtTokenProvider {

    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        ClassPathResource resource = new ClassPathResource("certs/public_key.pem");
        String key;
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            key = FileCopyUtils.copyToString(reader);
        }

        // Dùng Regex "quét sạch" mọi loại Header, Footer và khoảng trắng/xuống dòng
        String publicKeyPEM = key
                .replaceAll("-----BEGIN.*?-----", "") // Cắt mọi header bắt đầu bằng -----BEGIN và kết thúc bằng -----
                .replaceAll("-----END.*?-----", "")   // Cắt mọi footer
                .replaceAll("\\s+", "");              // Cắt toàn bộ dấu cách, \n, \r

        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        this.publicKey = keyFactory.generatePublic(keySpec);
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(authToken);
            return true;
        } catch (Exception ex) {
            // Log the exception if needed
        }
        return false;
    }

    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }
}

