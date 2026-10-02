package com.example.senioron.domain.family.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.senioron.domain.device.entity.Device;
import com.example.senioron.domain.device.entity.DeviceStatus;
import com.example.senioron.domain.device.repository.DeviceRepository;
import com.example.senioron.domain.family.entity.FamilyPhoto;
import com.example.senioron.domain.family.repository.FamilyPhotoGroupRepository;
import com.example.senioron.domain.family.repository.FamilyPhotoRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FamilyPhotoPushTargetServiceTest {

    private final FamilyPhotoRepository photos = mock(FamilyPhotoRepository.class);
    private final FamilyPhotoGroupRepository groups = mock(FamilyPhotoGroupRepository.class);
    private final DeviceRepository devices = mock(DeviceRepository.class);
    private final FamilyPhotoPushTargetService service =
            new FamilyPhotoPushTargetService(photos, groups, devices);

    @Test
    void deduplicatesTokensKeepsOfflineDevicesAndExcludesDisconnectedOrExpiredDevices() {
        var parents = stubRecipients("업로더");
        given(devices.findAllByUserIn(parents)).willReturn(List.of(
                device("same-token", DeviceStatus.ONLINE),
                device("same-token", DeviceStatus.ONLINE),
                device("offline-token", DeviceStatus.OFFLINE),
                device("disconnected-token", DeviceStatus.DISCONNECTED),
                device("expired-token", DeviceStatus.LOGIN_EXPIRED),
                device(null, DeviceStatus.ONLINE),
                device("  ", DeviceStatus.ONLINE)));

        var target = service.load(10L).orElseThrow();
        assertThat(target.tokens()).containsExactly("same-token", "offline-token");
        assertThat(target.body()).isEqualTo("업로더님이 새 가족사진을 공유했어요.");
    }

    @Test
    void skipsDeletedPhotoBeforeQueryingRecipients() {
        given(photos.findById(10L)).willReturn(Optional.empty());
        assertThat(service.load(10L)).isEmpty();
        verifyNoInteractions(groups, devices);
    }

    @Test
    void skipsWhenNoLinkedParentCanReceivePhoto() {
        stubRecipients("업로더");
        given(groups.findPhotoRecipientParents(10L, Role.PARENT, UserStatus.ACTIVE))
                .willReturn(List.of());
        assertThat(service.load(10L)).isEmpty();
        verifyNoInteractions(devices);
    }

    @Test
    void skipsWhenAllDeviceTokensAreUnavailable() {
        var parents = stubRecipients("업로더");
        given(devices.findAllByUserIn(parents)).willReturn(List.of(
                device(null, DeviceStatus.ONLINE),
                device("old-token", DeviceStatus.DISCONNECTED)));
        assertThat(service.load(10L)).isEmpty();
    }

    @Test
    void usesFallbackWhenUploaderNameIsBlank() {
        var parents = stubRecipients("  ");
        given(devices.findAllByUserIn(parents))
                .willReturn(List.of(device("token", DeviceStatus.ONLINE)));
        assertThat(service.load(10L).orElseThrow().body())
                .isEqualTo("자녀님이 새 가족사진을 공유했어요.");
    }

    private List<User> stubRecipients(String uploaderName) {
        User uploader = User.builder().usersId(1L).name(uploaderName).build();
        given(photos.findById(10L)).willReturn(Optional.of(
                FamilyPhoto.builder().familyPhotoId(10L).user(uploader).build()));
        var parents = List.of(User.builder().usersId(2L).name("부모").build());
        given(groups.findPhotoRecipientParents(10L, Role.PARENT, UserStatus.ACTIVE))
                .willReturn(parents);
        return parents;
    }

    private Device device(String token, DeviceStatus status) {
        return Device.builder().deviceToken(token).connectionStatus(status).build();
    }
}
