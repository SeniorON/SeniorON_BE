package com.example.senioron.domain.permission.repository;

import com.example.senioron.domain.permission.entity.SeniorPermissionSetting;
import com.example.senioron.domain.senior.entity.Senior;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorPermissionSettingRepository
        extends JpaRepository<SeniorPermissionSetting, Long> {

    void deleteAllBySenior(Senior senior);
}
