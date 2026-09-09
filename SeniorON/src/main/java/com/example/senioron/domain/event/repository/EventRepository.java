package com.example.senioron.domain.event.repository;

import com.example.senioron.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    @Modifying
    @Transactional
    @Query("UPDATE Event e SET e.address = :address WHERE e.eventId = :eventId")
    int updateAddressByEventId(@Param("eventId") Long eventId, @Param("address") String address);
}
