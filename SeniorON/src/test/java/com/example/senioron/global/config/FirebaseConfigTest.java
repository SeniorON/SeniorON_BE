package com.example.senioron.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

class FirebaseConfigTest {

    private final Environment environment = mock(Environment.class);

    @Test
    void prodProfileFailsFastWhenServiceAccountMissing() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"prod"});
        FirebaseConfig firebaseConfig = new FirebaseConfig(environment);
        ReflectionTestUtils.setField(firebaseConfig, "serviceAccountPath", "definitely-does-not-exist.json");

        assertThatThrownBy(firebaseConfig::init)
                .isInstanceOf(IllegalStateException.class);
        assertThat(firebaseConfig.isInitialized()).isFalse();
    }

    @Test
    void nonProdProfileSkipsWithoutThrowingWhenServiceAccountMissing() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"local"});
        FirebaseConfig firebaseConfig = new FirebaseConfig(environment);
        ReflectionTestUtils.setField(firebaseConfig, "serviceAccountPath", "definitely-does-not-exist.json");

        firebaseConfig.init();

        assertThat(firebaseConfig.isInitialized()).isFalse();
    }
}
