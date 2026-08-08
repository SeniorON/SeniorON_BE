package com.example.senioron.domain.inquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.BDDMockito.given;

import com.example.senioron.domain.inquiry.dto.response.InquiryDetailResponse;
import com.example.senioron.domain.inquiry.dto.response.InquiryListItemResponse;
import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import com.example.senioron.domain.inquiry.entity.InquiryImage;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import com.example.senioron.domain.inquiry.repository.InquiryRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.example.senioron.global.storage.S3Service;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class InquiryServiceTest {

    @Autowired
    private InquiryRepository inquiryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private final S3Service s3Service = org.mockito.Mockito.mock(S3Service.class);

    private InquiryService inquiryService;

    @BeforeEach
    void setUp() {
        inquiryService = new InquiryService(
                inquiryRepository,
                userRepository,
                s3Service
        );
    }

    @Test
    void getMyInquiriesReturnsOnlyCurrentUserInquiriesInLatestOrder() {
        User currentUser = saveUser("current");
        User otherUser = saveUser("other");

        Inquiry older = saveInquiry(
                currentUser,
                "older",
                InquiryStatus.WAITING,
                LocalDateTime.of(2026, 8, 7, 18, 30)
        );
        Inquiry latest = saveInquiry(
                currentUser,
                "latest",
                InquiryStatus.COMPLETED,
                LocalDateTime.of(2026, 8, 8, 14, 12)
        );
        saveInquiry(
                otherUser,
                "other",
                InquiryStatus.WAITING,
                LocalDateTime.of(2026, 8, 9, 10, 0)
        );

        entityManager.flush();
        entityManager.clear();

        List<InquiryListItemResponse> responses = inquiryService.getMyInquiries(currentUser);

        assertThat(responses)
                .extracting(InquiryListItemResponse::getInquiryId)
                .containsExactly(latest.getInquiryId(), older.getInquiryId());
        assertThat(responses)
                .extracting(InquiryListItemResponse::getTitle)
                .containsExactly("latest", "older");
    }

    @Test
    void getMyInquiriesReturnsEmptyListWhenCurrentUserHasNoInquiry() {
        User currentUser = saveUser("current");
        User otherUser = saveUser("other");
        saveInquiry(
                otherUser,
                "other",
                InquiryStatus.WAITING,
                LocalDateTime.of(2026, 8, 8, 14, 12)
        );

        entityManager.flush();
        entityManager.clear();

        List<InquiryListItemResponse> responses = inquiryService.getMyInquiries(currentUser);

        assertThat(responses).isEmpty();
    }

    @Test
    void getMyInquiryReturnsDetailWithImagesAndAnswers() {
        User currentUser = saveUser("current");
        Inquiry inquiry = saveInquiry(
                currentUser,
                "가족 코드를 잃어버렸어요",
                InquiryStatus.COMPLETED,
                LocalDateTime.of(2026, 8, 7, 18, 30)
        );
        inquiry.addImage(InquiryImage.builder()
                .inquiry(inquiry)
                .imageUrl("inquiries/1/image-1.png")
                .build());
        InquiryAnswer answer = InquiryAnswer.builder()
                .inquiry(inquiry)
                .content("가족코드는 앱의 설정 > 내 계정에서 다시 확인할 수 있습니다.")
                .createdAt(LocalDateTime.of(2026, 8, 7, 19, 15))
                .build();
        entityManager.persist(answer);
        given(s3Service.getFileUrl("inquiries/1/image-1.png"))
                .willReturn("https://example.com/image-1.png");

        entityManager.flush();
        entityManager.clear();

        InquiryDetailResponse response = inquiryService.getMyInquiry(
                currentUser,
                inquiry.getInquiryId()
        );

        assertThat(response.getInquiryId()).isEqualTo(inquiry.getInquiryId());
        assertThat(response.getTitle()).isEqualTo("가족 코드를 잃어버렸어요");
        assertThat(response.getContent()).isEqualTo("content");
        assertThat(response.getStatus()).isEqualTo(InquiryStatus.COMPLETED);
        assertThat(response.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 7, 18, 30));
        assertThat(response.getImages()).containsExactly("https://example.com/image-1.png");
        assertThat(response.getAnswers()).hasSize(1);
        assertThat(response.getAnswers().get(0).getAnswerId()).isEqualTo(answer.getInquiryAnswerId());
        assertThat(response.getAnswers().get(0).getContent())
                .isEqualTo("가족코드는 앱의 설정 > 내 계정에서 다시 확인할 수 있습니다.");
        assertThat(response.getAnswers().get(0).getCreatedAt())
                .isEqualTo(LocalDateTime.of(2026, 8, 7, 19, 15));
    }

    @Test
    void getMyInquiryReturnsEmptyImagesAndAnswersWhenMissing() {
        User currentUser = saveUser("current");
        Inquiry inquiry = saveInquiry(
                currentUser,
                "기기 연결 문의",
                InquiryStatus.WAITING,
                LocalDateTime.of(2026, 8, 8, 14, 12)
        );

        entityManager.flush();
        entityManager.clear();

        InquiryDetailResponse response = inquiryService.getMyInquiry(
                currentUser,
                inquiry.getInquiryId()
        );

        assertThat(response.getImages()).isEmpty();
        assertThat(response.getAnswers()).isEmpty();
    }

    @Test
    void getMyInquiryThrowsInquiryNotFoundWhenInquiryDoesNotExist() {
        User currentUser = saveUser("current");

        assertThatThrownBy(() -> inquiryService.getMyInquiry(currentUser, 999L))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    @Test
    void getMyInquiryThrowsInquiryNotFoundWhenInquiryBelongsToOtherUser() {
        User currentUser = saveUser("current");
        User otherUser = saveUser("other");
        Inquiry otherInquiry = saveInquiry(
                otherUser,
                "other",
                InquiryStatus.WAITING,
                LocalDateTime.of(2026, 8, 8, 14, 12)
        );

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> inquiryService.getMyInquiry(currentUser, otherInquiry.getInquiryId()))
                .isInstanceOf(BusinessException.class)
                .asInstanceOf(type(BusinessException.class))
                .extracting(BusinessException::getCode)
                .isEqualTo(ErrorCode.INQUIRY_NOT_FOUND);
    }

    private User saveUser(String prefix) {
        String unique = UUID.randomUUID().toString();

        return userRepository.saveAndFlush(
                User.builder()
                        .loginId(prefix + "-" + unique)
                        .email(prefix + "-" + unique + "@test.com")
                        .password("encoded-password")
                        .name(prefix)
                        .role(Role.CHILD)
                        .build()
        );
    }

    private Inquiry saveInquiry(
            User user,
            String title,
            InquiryStatus status,
            LocalDateTime createdAt
    ) {
        Inquiry inquiry = Inquiry.builder()
                .user(user)
                .title(title)
                .content("content")
                .status(status)
                .createdAt(createdAt)
                .build();

        return inquiryRepository.saveAndFlush(inquiry);
    }
}
