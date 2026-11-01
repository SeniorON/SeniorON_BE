
package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import com.example.senioron.domain.inquiry.entity.InquiryAnswer;
import com.example.senioron.domain.inquiry.entity.InquiryImage;
import com.example.senioron.domain.inquiry.entity.InquiryStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminInquiryDetailResponse {

    private Long inquiryId;
    private String title;
    private String content;
    private String writerName;
    private InquiryStatus status;
    private LocalDateTime createdAt;

    private String deviceModel;
    private String osVersion;
    private String appVersion;

    private List<ImageResponse> images;
    private List<AnswerResponse> answers;

    public static AdminInquiryDetailResponse from(
            Inquiry inquiry,
            List<ImageResponse> images
    ) {
        return AdminInquiryDetailResponse.builder()
                .inquiryId(inquiry.getInquiryId())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .writerName(
                        AdminInquiryListItemResponse
                                .from(inquiry)
                                .getWriterName()
                )
                .status(inquiry.getStatus())
                .createdAt(inquiry.getCreatedAt())
                .deviceModel(inquiry.getDeviceModel())
                .osVersion(inquiry.getOsVersion())
                .appVersion(inquiry.getAppVersion())
                .images(images)
                .answers(
                        inquiry.getAnswers()
                                .stream()
                                .sorted(
                                        Comparator
                                                .comparing(InquiryAnswer::getCreatedAt)
                                                .thenComparing(InquiryAnswer::getInquiryAnswerId)
                                )
                                .map(AnswerResponse::from)
                                .toList()
                )
                .build();
    }

    @Getter
    @Builder
    public static class ImageResponse {

        private Long imageId;
        private String imageUrl;
        private String originalFileName;

        public static ImageResponse from(
                InquiryImage image,
                String imageUrl
        ) {
            return ImageResponse.builder()
                    .imageId(image.getInquiryImageId())
                    .imageUrl(imageUrl)
                    .originalFileName(image.getOriginalFileName())
                    .build();
        }
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
