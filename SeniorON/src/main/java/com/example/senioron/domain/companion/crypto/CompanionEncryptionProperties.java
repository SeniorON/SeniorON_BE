package com.example.senioron.domain.companion.crypto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "companion.encryption")
public class CompanionEncryptionProperties {

    private String key = "";
}
