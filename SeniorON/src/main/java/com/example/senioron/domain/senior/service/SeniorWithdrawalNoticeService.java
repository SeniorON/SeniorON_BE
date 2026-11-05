package com.example.senioron.domain.senior.service;

import com.example.senioron.domain.senior.dto.response.SeniorWithdrawalNoticeResponse;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNotice;
import com.example.senioron.domain.senior.entity.SeniorWithdrawalNoticeRecipient;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRepository;
import com.example.senioron.domain.senior.repository.SeniorWithdrawalNoticeRecipientRepository;
import com.example.senioron.domain.user.entity.Role;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeniorWithdrawalNoticeService {

    private final UserRepository userRepository;
    private final SeniorWithdrawalNoticeRepository noticeRepository;
    private final SeniorWithdrawalNoticeRecipientRepository recipientRepository;

    public List<SeniorWithdrawalNoticeResponse> getUnacknowledgedNotices(User principal) {
        User child = getAuthenticatedChild(principal);
        LocalDateTime now = LocalDateTime.now();

        return recipientRepository
                .findUnacknowledgedAndUnexpiredByRecipientUserId(
                        child.getUsersId(),
                        now
                )
                .stream()
                .map(SeniorWithdrawalNoticeResponse::from)
                .toList();
    }

    @Transactional
    public void acknowledgeNotice(User principal, Long noticeId) {
        User child = getAuthenticatedChild(principal);
        SeniorWithdrawalNotice notice = noticeRepository.findByIdForUpdate(noticeId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SENIOR_WITHDRAWAL_NOTICE_NOT_FOUND
                ));
        SeniorWithdrawalNoticeRecipient recipient = recipientRepository
                .findByNoticeIdAndRecipientUserId(noticeId, child.getUsersId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SENIOR_WITHDRAWAL_NOTICE_ACCESS_DENIED
                ));

        if (recipient.getAcknowledgedAt() != null) {
            return;
        }

        LocalDateTime acknowledgedAt = LocalDateTime.now();
        if (!notice.getExpiresAt().isAfter(acknowledgedAt)) {
            throw new BusinessException(ErrorCode.SENIOR_WITHDRAWAL_NOTICE_EXPIRED);
        }

        recipient.acknowledge(acknowledgedAt);
        recipientRepository.flush();

        boolean hasUnacknowledgedRecipient =
                recipientRepository
                        .existsByNoticeSeniorWithdrawalNoticeIdAndAcknowledgedAtIsNull(
                                noticeId
                        );
        if (!hasUnacknowledgedRecipient) {
            notice.complete(acknowledgedAt);
        }
    }

    private User getAuthenticatedChild(User principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED);
        }

        User user = userRepository.findById(principal.getUsersId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getRole() != Role.CHILD) {
            throw new BusinessException(ErrorCode.SENIOR_WITHDRAWAL_NOTICE_CHILD_ONLY);
        }

        return user;
    }
}
