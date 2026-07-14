package com.example.senioron.global.config;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
public class FirebaseConfig {

    @Getter
    private boolean initialized = false;
    @Value("${firebase.service-account-path}")
    private String serviceAccountPath;

    @PostConstruct
    public void init(){

        try {
            InputStream serviceAccount =
                    new ClassPathResource(serviceAccountPath).getInputStream();
            FirebaseOptions options =
                    FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount)).build();

            if(FirebaseApp.getApps().isEmpty()){
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
        } catch(Exception e){
            log.warn("Firebase 초기화 실패, FCM 기능이 비활성화됩니다.", e);
        }
    }
}
