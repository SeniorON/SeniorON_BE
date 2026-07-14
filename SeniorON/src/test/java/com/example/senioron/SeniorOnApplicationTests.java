package com.example.senioron;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "cloud.aws.region=ap-northeast-2",
        "cloud.aws.s3.bucket=test-bucket"
})
class SeniorOnApplicationTests {

    @Test
    void contextLoads() {
    }

}
