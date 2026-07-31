package com.example.senioron.domain.companion.service.validation;

import com.example.senioron.domain.companion.service.model.VoiceAudio;
import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class VoiceFileValidator {

    private static final long MAX_AUDIO_SIZE =
            10L * 1024 * 1024;

    public VoiceAudio validateAndConvert(
            MultipartFile file
    ) {
        validateRequired(file);
        validateSize(file);

        String filename = sanitizeFilename(
                file.getOriginalFilename()
        );

        String extension = extractExtension(filename);
        validateFormat(extension, file.getContentType());

        try {
            return new VoiceAudio(
                    filename,
                    file.getContentType(),
                    file.getBytes()
            );
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private static final Map<String, Set<String>>
            ALLOWED_AUDIO_TYPES = Map.ofEntries(
            Map.entry(
                    "m4a",
                    Set.of("audio/mp4", "audio/x-m4a")
            ),
            Map.entry(
                    "mp3",
                    Set.of("audio/mpeg")
            ),
            Map.entry(
                    "mp4",
                    Set.of("audio/mp4")
            ),
            Map.entry(
                    "mpeg",
                    Set.of("audio/mpeg")
            ),
            Map.entry(
                    "mpga",
                    Set.of("audio/mpeg")
            ),
            Map.entry(
                    "wav",
                    Set.of("audio/wav", "audio/x-wav")
            ),
            Map.entry(
                    "webm",
                    Set.of("audio/webm")
            ),
            Map.entry(
                    "ogg",
                    Set.of("audio/ogg")
            ),
            Map.entry(
                    "flac",
                    Set.of("audio/flac")
            )
    );

    private void validateRequired(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_REQUIRED);
        }
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_AUDIO_SIZE) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_SIZE_EXCEEDED);
        }
    }

    private String sanitizeFilename(
            String originalFilename
    ) {
        if (originalFilename == null
                || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED);
        }

        String normalized =
                originalFilename.replace('\\', '/');

        String filename = normalized.substring(normalized.lastIndexOf('/') + 1
        );

        if (filename.isBlank()) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED);
        }

        return filename;
    }

    private void validateFormat(
            String extension,
            String contentType
    ) {
        Set<String> allowedContentTypes = ALLOWED_AUDIO_TYPES.get(extension);

        if (allowedContentTypes == null
                || contentType == null
                || !allowedContentTypes.contains(
                contentType.toLowerCase(Locale.ROOT)
        )) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED);
        }
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex <= 0
                || dotIndex == filename.length() - 1) {
            throw new BusinessException(ErrorCode.COMPANION_AUDIO_FORMAT_UNSUPPORTED);
        }

        return filename.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }
}
