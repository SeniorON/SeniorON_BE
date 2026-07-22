package com.example.senioron.domain.device.repository;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository
        extends JpaRepository<Device, Long> {

    Optional<Device> findFirstByUser(User user);

    List<Device> findAllByUserIn(List<User> users);
}