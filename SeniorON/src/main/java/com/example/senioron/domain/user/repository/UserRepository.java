package com.example.senioron.domain.user.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    Optional<User> findByLoginId(String loginId);

    List<User> findAllByFamily(Family family);

    @Query("SELECT u FROM User u WHERE u.family = :family AND u.usersId != :excludeUserId AND u.role = :role")
    List<User> findByFamilyAndUsersIdNotAndRole(
            @Param("family")Family family,
            @Param("excludeUserId")Long excludeUserId,
            @Param("role")Role role);

    Optional<User> findByFcmToken(String fcmToken);
}