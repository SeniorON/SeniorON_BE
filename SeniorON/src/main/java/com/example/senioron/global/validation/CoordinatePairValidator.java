package com.example.senioron.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordinatePairValidator implements ConstraintValidator<ValidCoordinatePair, CoordinatePairRequest> {

    @Override
    public boolean isValid(CoordinatePairRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean hasLatitude = request.latitude() != null;
        boolean hasLongitude = request.longitude() != null;

        return hasLatitude == hasLongitude;
    }
}
