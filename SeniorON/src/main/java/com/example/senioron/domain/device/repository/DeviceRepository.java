package com.example.senioron.domain.device.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository
        extends JpaRepository<Device, Long> {

    Optional<Device> findFirstByUserOrderByLastConnectedAtDescDeviceIdDesc(
            User user
    );

    List<Device> findAllByUserIn(List<User> users);

    @Modifying
    @Query("""
            UPDATE Device d
            SET d.deviceToken = null
            WHERE d.deviceToken = :token
            """)
    void clearDeviceToken(
            @Param("token") String token
    );

    List<Device> findAllByUser(User user);

    Optional<Device> findByUserAndDeviceIdentifier(
            User user,
            String deviceIdentifier
    );

    void deleteAllByUser(User user);

    List<Device> findAllByUser_FamilyAndUser_Role(
            Family family,
            Role role
    );
}
