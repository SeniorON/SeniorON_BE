package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeniorRepository extends JpaRepository<Senior, Long> {

    List<Senior> findAllByRegisteredBy(User registeredBy);

    Optional<Senior> findFirstByRegisteredBy(User registeredBy);
}