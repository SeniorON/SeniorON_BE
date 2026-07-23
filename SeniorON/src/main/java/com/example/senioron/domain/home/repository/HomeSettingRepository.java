package com.example.senioron.domain.home.repository;

import com.example.senioron.domain.home.entity.HomeSetting;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HomeSettingRepository
        extends JpaRepository<HomeSetting, Long> {

    Optional<HomeSetting> findByUser(User user);
}