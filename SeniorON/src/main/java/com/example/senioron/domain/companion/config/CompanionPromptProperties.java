package com.example.senioron.domain.companion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "companion.prompt")
public class CompanionPromptProperties {

    private Resource resource = new ClassPathResource("prompts/companion-system-prompt.txt");

    private String version = "v1";

    private int recentMessageLimit = 20;
}