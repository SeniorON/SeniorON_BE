package com.example.senioron.domain.device.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
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

    // device_identifier는 기기 기준 유니크 키라 계정과 무관하게 기기 하나당 row가 하나만 존재한다.
    Optional<Device> findByDeviceIdentifier(String deviceIdentifier);

    // 소유자 확인과 토큰/연결상태 초기화를 하나의 UPDATE로 묶어, 로그아웃 처리 중
    // 다른 계정이 재로그인해 소유자가 바뀌는 경쟁 상태에서도 그 계정의 값을 덮어쓰지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Device d
            SET d.deviceToken = null, d.connectionStatus = :status
            WHERE d.deviceIdentifier = :deviceIdentifier
            AND d.user.usersId = :usersId
            """)
    int clearTokenIfOwnedBy(
            @Param("deviceIdentifier") String deviceIdentifier,
            @Param("usersId") Long usersId,
            @Param("status") DeviceStatus status
    );
    List<Device> findAllByUser_FamilyAndUser_Role(
            Family family,
            Role role
    );
}