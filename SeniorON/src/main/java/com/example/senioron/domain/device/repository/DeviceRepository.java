package com.example.senioron.domain.device.repository;

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

    Optional<Device>
    findFirstByUserAndLastLocationUpdatedAtIsNotNullOrderByLastLocationUpdatedAtDescDeviceIdDesc(
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

    // device_identifier는 기기 기준 유니크 키라 계정과 무관하게 기기 하나당 row가 하나만 존재
    Optional<Device> findByDeviceIdentifier(String deviceIdentifier);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Device d
        SET d.deviceToken = null
        WHERE d.deviceIdentifier = :deviceIdentifier
        AND d.user.usersId = :usersId
        """)
    int clearTokenIfOwnedBy(
            @Param("deviceIdentifier") String deviceIdentifier,
            @Param("usersId") Long usersId
    );

    void deleteAllByUser(User user);

    Optional<Device> findByDeviceIdentifierAndUser(
            String deviceIdentifier,
            User user
    );

}
