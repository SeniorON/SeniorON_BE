package com.example.senioron.domain.inactivity.repository;

import com.example.senioron.domain.inactivity.entity.InactivitySetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InactivitySettingRepository extends JpaRepository<InactivitySetting, Long> {
}
