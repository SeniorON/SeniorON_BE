package com.example.senioron.domain.companion.service.model;

public record TurnCreationResult(
        TurnCreationStatus status,
        Long createdTurnId
) {

    public static TurnCreationResult created(Long turnId) {
        return new TurnCreationResult(TurnCreationStatus.CREATED, turnId);
    }

    public static TurnCreationResult alreadyExists() {
        return new TurnCreationResult(TurnCreationStatus.ALREADY_EXISTS, null);
    }
}