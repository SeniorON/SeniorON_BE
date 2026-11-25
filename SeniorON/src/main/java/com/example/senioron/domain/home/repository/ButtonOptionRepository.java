package com.example.senioron.domain.home.repository;

import com.example.senioron.domain.home.entity.ButtonOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ButtonOptionRepository
        extends JpaRepository<ButtonOption, Long> {
}