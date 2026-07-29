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

    // FCM 요청 1회당 상한. SDK가 503에 대해 내부적으로 최대 4회, 최대 7.5초 백오프로 재시도하므로
    // 이 값은 "총 대기시간 상한"이 아니라 "요청 하나가 멈춰있는 시간"의 상한이다.
    private static final int FCM_REQUEST_TIMEOUT_MILLIS = 3000;

    @Getter
    private boolean initialized = false;
    @Value("${firebase.service-account-path}")
    private String serviceAccountPath;

    @PostConstruct
    public void init(){

        try {
            InputStream serviceAccount =
                    new ClassPathResource(serviceAccountPath).getInputStream();
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setConnectTimeout(FCM_REQUEST_TIMEOUT_MILLIS)
                    .setReadTimeout(FCM_REQUEST_TIMEOUT_MILLIS)
                    .setWriteTimeout(FCM_REQUEST_TIMEOUT_MILLIS)
                    .build();

            if(FirebaseApp.getApps().isEmpty()){
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
        } catch(Exception e){
            log.warn("Firebase 초기화 실패, FCM 기능이 비활성화됩니다.", e);
        }
    }
}
