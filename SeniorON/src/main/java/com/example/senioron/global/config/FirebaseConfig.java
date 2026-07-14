package com.example.senioron.global.config;

import com.example.senioron.global.apiPayload.code.ErrorCode;
import com.example.senioron.global.apiPayload.exception.BusinessException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
@Slf4j
public class FirebaseConfig {

    @PostConstruct
    public void init(){
        try {
            InputStream serviceAccount =
                    new ClassPathResource("senioron-8c2e1-firebase-adminsdk-fbsvc-ba018605fb.json").getInputStream();
            FirebaseOptions options =
                    FirebaseOptions.builder().setCredentials(GoogleCredentials.fromStream(serviceAccount)).build();

            if(FirebaseApp.getApps().isEmpty()){
                FirebaseApp.initializeApp(options);
            }

        } catch(Exception e){
            log.error(e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }
}
