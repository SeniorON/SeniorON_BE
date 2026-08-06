package com.example.senioron.domain.companion.service.safety;

import java.util.Optional;

public interface EmergencyRuleDetector {

    Optional<String> detectRuleId(
            String currentUtterance
    );
}
