package iuh.fit.auth_service.service;

import iuh.fit.auth_service.entity.AuthProvider;
import iuh.fit.auth_service.entity.AccountLifecycleStatus;
import iuh.fit.auth_service.entity.AuthUser;
import iuh.fit.auth_service.entity.UserRole;
import iuh.fit.auth_service.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {
    private final AuthUserRepository authUserRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminEmail = "admin@cab.local";
        if (!authUserRepository.existsByEmailIgnoreCase(adminEmail)) {
            log.info("Seeding default admin user...");
            AuthUser admin = new AuthUser();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode("AdminPassword123"));
            admin.setFullName("System Administrator");
            admin.setPhoneNumber("0999999999");
            admin.setAvatarUrl("https://ui-avatars.com/api/?name=System+Administrator");
            admin.setRole(UserRole.ADMIN);
            admin.setProvider(AuthProvider.LOCAL_EMAIL);
            admin.setEmailVerified(true);
            admin.setActive(true);
            admin.setAccountStatus(AccountLifecycleStatus.ACTIVE);
            admin.setLastLoginAt(LocalDateTime.now());
            
            authUserRepository.save(admin);
            log.info("Default admin user seeded successfully!");
        } else {
            log.info("Default admin user already exists.");
        }
    }
}
