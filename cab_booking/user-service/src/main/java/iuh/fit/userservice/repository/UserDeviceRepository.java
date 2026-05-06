package iuh.fit.userservice.repository;

import iuh.fit.userservice.entity.UserDevice;
import iuh.fit.userservice.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    List<UserDevice> findByUserProfileOrderByLastSeenAtDesc(UserProfile userProfile);

    Optional<UserDevice> findByIdAndUserProfileExternalUserId(UUID id, String externalUserId);

    Optional<UserDevice> findByUserProfileAndDeviceIdentifier(UserProfile userProfile, String deviceIdentifier);
}
