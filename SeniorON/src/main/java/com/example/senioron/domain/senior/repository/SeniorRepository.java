package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeniorRepository extends JpaRepository<Senior, Long> {

    List<Senior> findAllByRegisteredBy(User registeredBy);
}