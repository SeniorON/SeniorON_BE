package com.example.senioron.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseConfig {

    // FCM 요청 1회당 상한. SDK가 503에 대해 내부적으로 최대 4회, 최대 7.5초 백오프로 재시도하므로
    // 이 값은 "총 대기시간 상한"이 아니라 "요청 하나가 멈춰있는 시간"의 상한이다.
    private static final int FCM_REQUEST_TIMEOUT_MILLIS = 3000;
    private static final String PROD_PROFILE = "prod";

    private final Environment environment;

    // 주의: 여기에 웹 ApplicationContext를 ResourceLoader로 주입받으면 안 된다.
    // DefaultResourceLoader는 그런 오버라이드가 없어 항상 classpath:/file:/일반 경로 규칙을 그대로 따른다.
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Getter
    private boolean initialized = false;
    // 접두어 없음(로컬): 클래스패스(JAR 내부)에서 찾는다 — 기존 로컬 동작과 동일.
    // "file:/절대경로" (배포): 이미지에 파일을 넣지 않고, 배포 시 서버에 마운트된 파일을 읽는다.
    // (Docker Hub 이미지가 public이라 서비스 계정 키를 이미지에 baked-in하면 키가 그대로 공개된다.)
    @Value("${firebase.service-account-path}")
    private String serviceAccountPath;

    @PostConstruct
    public void init(){

        try (InputStream serviceAccount =
                     resourceLoader.getResource(serviceAccountPath).getInputStream()) {
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
            // 운영 환경에서 조용히 넘어가면 알림이 계속 발송되지 않는 상태를 아무도 알아채지 못하므로,
            // prod에서는 초기화 실패 시 기동 자체를 막는다.
            if (isProdProfile()) {
                throw new IllegalStateException(
                        "[prod] Firebase 서비스 계정 초기화에 실패해 애플리케이션을 시작할 수 없습니다. "
                                + "배포 서버의 서비스 계정 키 파일/마운트 설정을 확인하세요.", e);
            }
            log.warn("Firebase 초기화 실패, FCM 기능이 비활성화됩니다.", e);
        }
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains(PROD_PROFILE);
    }
}
