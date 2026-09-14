package com.example.senioron.domain.family.repository;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoGroupFamilyRepository
        extends JpaRepository<PhotoGroupFamily, Long> {

    @EntityGraph(attributePaths = "photoGroup")
    Optional<PhotoGroupFamily> findFirstByFamilyOrderByIdAsc(Family family);

    @EntityGraph(attributePaths = "photoGroup")
    List<PhotoGroupFamily> findAllByFamilyOrderByIdAsc(Family family);

    boolean existsByFamilyAndPhotoGroup(Family family, PhotoGroup photoGroup);
}
