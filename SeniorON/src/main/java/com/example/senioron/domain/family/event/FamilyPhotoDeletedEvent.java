package com.example.senioron.domain.family.event;

import java.util.List;

public record FamilyPhotoDeletedEvent(
        Long familyPhotoId,
        List<String> tokens
) {
    public FamilyPhotoDeletedEvent {
        tokens = List.copyOf(tokens);
    }
}