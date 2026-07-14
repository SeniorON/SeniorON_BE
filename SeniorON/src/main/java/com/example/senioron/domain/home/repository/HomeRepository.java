package com.example.senioron.domain.home.repository;

import com.example.senioron.domain.home.entity.ActionType;
import com.example.senioron.domain.home.entity.Home;
import com.example.senioron.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HomeRepository extends JpaRepository<Home, Long> {

    List<Home> findAllByUserOrderByButtonOrderAsc(User user);

    boolean existsByUserAndActionTypeAndActionValue(
            User user,
            ActionType actionType,
            String actionValue
    );

    void deleteAllByHomeIdIn(List<Long> homeIds);
}