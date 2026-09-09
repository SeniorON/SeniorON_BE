package com.example.senioron.global.apiPayload.exception;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadSizeExceptionTest {
    @Test
    void photoUploadStillReturnsStructuredPayloadTooLargeResponse() throws Exception {
        MockMvcBuilders.standaloneSetup(new OversizedPhotoController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build()
                .perform(post("/api/family/photos"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.PAYLOAD_TOO_LARGE.getCode()));
    }

    @RestController
    static class OversizedPhotoController {
        @PostMapping("/api/family/photos")
        void upload() {
            throw new MaxUploadSizeExceededException(10 * 1024 * 1024);
        }
    }
}
