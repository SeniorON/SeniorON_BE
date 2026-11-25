package com.example.senioron;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class SeniorOnApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeniorOnApplication.class, args);
    }

}
