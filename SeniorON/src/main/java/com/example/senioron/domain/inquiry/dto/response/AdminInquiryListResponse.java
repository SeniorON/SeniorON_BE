
package com.example.senioron.domain.inquiry.dto.response;

import com.example.senioron.domain.inquiry.entity.Inquiry;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class AdminInquiryListResponse {

    private List<AdminInquiryListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static AdminInquiryListResponse from(Page<Inquiry> inquiries) {
        return AdminInquiryListResponse.builder()
                .content(
                        inquiries.getContent()
                                .stream()
                                .map(AdminInquiryListItemResponse::from)
                                .toList()
                )
                .page(inquiries.getNumber())
                .size(inquiries.getSize())
                .totalElements(inquiries.getTotalElements())
                .totalPages(inquiries.getTotalPages())
                .build();
    }
}
