package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InquiryDetailResponse {

    private Long inquiryId;
    private String title;
    private String content;
    private InquiryStatus status;
    private LocalDateTime createdAt;
    private List<String> images;
    private List<AnswerResponse> answers;

    public static InquiryDetailResponse from(
            Inquiry inquiry,
            List<String> images
    ) {
        return InquiryDetailResponse.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .images(images)
                .answers(inquiry.getAnswers().stream()
                        .map(AnswerResponse::from)
                        .toList())
                .build();
    }

    @Getter
    @Builder
    public static class AnswerResponse {

        private Long answerId;
        private String content;
        private LocalDateTime createdAt;

        public static AnswerResponse from(InquiryAnswer answer) {
            return AnswerResponse.builder()
                    .answerId(answer.getInquiryAnswerId())
                    .content(answer.getContent())
                    .createdAt(answer.getCreatedAt())
                    .build();
        }
    }
}
