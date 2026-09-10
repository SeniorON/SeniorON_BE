package com.example.senioron.domain.senior.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.senior.entity.UserSenior;
import com.example.senioron.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSeniorRepository extends JpaRepository<UserSenior, Long> {

    boolean existsByUserAndSenior(User user, Senior senior);

    Optional<UserSenior> findByUserAndSenior(User user, Senior senior);

    Optional<UserSenior> findFirstByUserAndSenior_FamilyOrderByUserSeniorIdAsc(User user, Family family);

    @EntityGraph(attributePaths = "senior")
    List<UserSenior> findAllByUserOrderByUserSeniorIdAsc(User user);

    void deleteAllBySenior(Senior senior);

    void deleteAllByUser(User user);
}
