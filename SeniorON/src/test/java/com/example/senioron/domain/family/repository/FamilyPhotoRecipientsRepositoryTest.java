package com.example.senioron.domain.family.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.family.entity.Family;
import com.example.senioron.domain.family.entity.FamilyMember;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.entity.FamilyPhotoSenior;
import com.example.senioron.domain.family.entity.PhotoGroup;
import com.example.senioron.domain.senior.entity.Senior;
import com.example.senioron.domain.user.entity.ManagerType;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false)
class FamilyPhotoRecipientsRepositoryTest {

    @Autowired private EntityManager entityManager;
    @Autowired private FamilyPhotoSeniorRepository shares;
    private User uploader;
    private FamilyPhoto photo;

    @BeforeEach
    void setUp() {
        uploader = persist(User.builder().name("업로더").role(Role.CHILD).build());
        PhotoGroup group = persist(PhotoGroup.builder().name("기본 그룹").build());
        photo = persist(FamilyPhoto.builder().user(uploader).photoGroup(group)
                .imageKey("recipient-test.jpg").build());
    }

    @Test
    void returnsParentsOfRequestedSharedSeniorsOnly() {
        Recipient first = recipient(Role.PARENT, UserStatus.ACTIVE, true, true, true);
        Recipient second = recipient(Role.PARENT, UserStatus.ACTIVE, true, true, true);
        entityManager.flush();

        assertThat(findRecipients(first, second))
                .extracting(User::getUsersId)
                .containsExactlyInAnyOrder(first.parent().getUsersId(), second.parent().getUsersId());
        assertThat(findRecipients(first))
                .extracting(User::getUsersId)
                .containsExactly(first.parent().getUsersId());
        assertThat(shares.findSeniorIdsByFamilyPhotoId(photo.getFamilyPhotoId()))
                .containsExactlyInAnyOrder(first.senior().getSeniorId(), second.senior().getSeniorId());
    }

    @Test
    void excludesUnsharedUnlinkedWithdrawnNonParentAndNonMemberAccounts() {
        Recipient eligible = recipient(Role.PARENT, UserStatus.ACTIVE, true, true, true);
        Recipient unlinked = recipient(Role.PARENT, UserStatus.ACTIVE, false, true, true);
        Recipient withdrawn = recipient(Role.PARENT, UserStatus.WITHDRAWN, true, true, true);
        Recipient child = recipient(Role.CHILD, UserStatus.ACTIVE, true, true, true);
        Recipient nonMember = recipient(Role.PARENT, UserStatus.ACTIVE, true, false, true);
        Recipient unshared = recipient(Role.PARENT, UserStatus.ACTIVE, true, true, false);
        entityManager.flush();

        assertThat(findRecipients(eligible, unlinked, withdrawn, child, nonMember, unshared))
                .extracting(User::getUsersId).containsExactly(eligible.parent().getUsersId());
    }

    @Test
    void stopsReturningParentAfterFamilyMembershipIsRemoved() {
        Recipient recipient = recipient(Role.PARENT, UserStatus.ACTIVE, true, true, true);
        entityManager.flush();
        assertThat(findRecipients(recipient)).hasSize(1);

        entityManager.remove(recipient.member());
        entityManager.flush();

        assertThat(findRecipients(recipient)).isEmpty();
    }

    private List<User> findRecipients(Recipient... recipients) {
        return shares.findRecipientParents(
                photo.getFamilyPhotoId(),
                List.of(recipients).stream().map(recipient -> recipient.senior().getSeniorId()).toList(),
                Role.PARENT,
                UserStatus.ACTIVE
        );
    }

    private Recipient recipient(Role role, UserStatus status, boolean linked, boolean member, boolean shared) {
        Family family = persist(Family.builder().build());
        User parent = persist(User.builder().name("수신자").role(role).status(status).build());
        Senior senior = persist(Senior.builder().name("시니어").birth(LocalDate.of(1950, 1, 1))
                .phoneNumber("01012345678").family(family).registeredBy(uploader)
                .parentUser(linked ? parent : null).build());
        if (shared) {
            persist(FamilyPhotoSenior.builder().familyPhoto(photo).senior(senior).build());
        }
        FamilyMember membership = member ? persist(FamilyMember.builder().family(family)
                .user(parent).managerType(ManagerType.NONE).build()) : null;
        return new Recipient(senior, parent, membership);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private record Recipient(Senior senior, User parent, FamilyMember member) {
    }
}
