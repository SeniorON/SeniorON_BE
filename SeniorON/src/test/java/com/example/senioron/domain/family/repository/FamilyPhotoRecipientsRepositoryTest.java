package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.family.entity.PhotoGroupFamily;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false)
class FamilyPhotoRecipientsRepositoryTest {

    @Autowired private EntityManager entityManager;
    @Autowired private FamilyPhotoGroupRepository groups;
    private User uploader;
    private PhotoGroup representative;
    private PhotoGroup shared;
    private FamilyPhoto photo;

    @BeforeEach
    void setUp() {
        uploader = persist(User.builder().name("업로더").role(Role.CHILD).build());
        representative = persist(PhotoGroup.builder().name("대표 그룹").build());
        shared = persist(PhotoGroup.builder().name("추가 공유 그룹").build());
        photo = persist(FamilyPhoto.builder().user(uploader).photoGroup(representative)
                .imageKey("recipient-test.jpg").build());
        persist(FamilyPhotoGroup.builder().familyPhoto(photo).photoGroup(representative).build());
        persist(FamilyPhotoGroup.builder().familyPhoto(photo).photoGroup(shared).build());
    }

    @Test
    void returnsBothSharedFamiliesAndDeduplicatesParentAcrossPhotoGroups() {
        Recipient first = recipient(representative, Role.PARENT, UserStatus.ACTIVE, true, true);
        persist(PhotoGroupFamily.builder().family(first.family()).photoGroup(shared).build());
        Recipient second = recipient(shared, Role.PARENT, UserStatus.ACTIVE, true, true);
        entityManager.flush();

        assertThat(groups.findPhotoRecipientParents(photo.getFamilyPhotoId(), Role.PARENT, UserStatus.ACTIVE))
                .extracting(User::getUsersId)
                .containsExactlyInAnyOrder(first.parent().getUsersId(), second.parent().getUsersId());
    }

    @Test
    void excludesUnsharedUnlinkedWithdrawnNonParentAndNonMemberAccounts() {
        Recipient eligible = recipient(shared, Role.PARENT, UserStatus.ACTIVE, true, true);
        recipient(shared, Role.PARENT, UserStatus.ACTIVE, false, true);
        recipient(shared, Role.PARENT, UserStatus.WITHDRAWN, true, true);
        recipient(shared, Role.CHILD, UserStatus.ACTIVE, true, true);
        recipient(shared, Role.PARENT, UserStatus.ACTIVE, true, false);
        PhotoGroup unshared = persist(PhotoGroup.builder().name("미공유 그룹").build());
        recipient(unshared, Role.PARENT, UserStatus.ACTIVE, true, true);
        entityManager.flush();

        assertThat(groups.findPhotoRecipientParents(photo.getFamilyPhotoId(), Role.PARENT, UserStatus.ACTIVE))
                .extracting(User::getUsersId).containsExactly(eligible.parent().getUsersId());
    }

    @Test
    void stopsReturningParentAfterFamilyMembershipIsRemoved() {
        Recipient recipient = recipient(shared, Role.PARENT, UserStatus.ACTIVE, true, true);
        entityManager.flush();
        assertThat(groups.findPhotoRecipientParents(photo.getFamilyPhotoId(), Role.PARENT, UserStatus.ACTIVE))
                .hasSize(1);

        entityManager.remove(recipient.member());
        entityManager.flush();

        assertThat(groups.findPhotoRecipientParents(photo.getFamilyPhotoId(), Role.PARENT, UserStatus.ACTIVE))
                .isEmpty();
    }

    private Recipient recipient(PhotoGroup group, Role role, UserStatus status,
                                boolean linked, boolean member) {
        Family family = persist(Family.builder().build());
        User parent = persist(User.builder().name("수신자").role(role).status(status).build());
        persist(Senior.builder().name("시니어").birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678").family(family).registeredBy(uploader)
                .parentUser(linked ? parent : null).build());
        persist(PhotoGroupFamily.builder().family(family).photoGroup(group).build());
        FamilyMember membership = member ? persist(FamilyMember.builder().family(family)
                .user(parent).managerType(ManagerType.NONE).build()) : null;
        return new Recipient(family, parent, membership);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Recipient(Family family, User parent, FamilyMember member) {
    }
}
