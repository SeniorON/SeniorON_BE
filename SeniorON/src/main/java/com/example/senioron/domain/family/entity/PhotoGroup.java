package com.example.senioron.domain.family.entity;

import com.example.senioron.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "photo_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PhotoGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photo_group_id")
    private Long id;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "photoGroup")
    @Builder.Default
    private List<PhotoGroupFamily> photoGroupFamilies = new ArrayList<>();

    @OneToMany(mappedBy = "photoGroup")
    @Builder.Default
    private List<FamilyPhoto> familyPhotos = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    public void disconnect() {
        this.active = false;
        this.disconnectedAt = LocalDateTime.now();
    }
}
