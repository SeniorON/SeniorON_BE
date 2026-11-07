package com.example.senioron.domain.user.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import org.junit.jupiter.api.Test;

class UserNameMaskerTest {

    @Test
    void 세_글자_이상은_첫_글자와_마지막_글자만_남긴다() {
        assertThat(UserNameMasker.mask(user("박지훈"))).isEqualTo("박*훈");
        assertThat(UserNameMasker.mask(user("남궁민수"))).isEqualTo("남*수");
    }

    @Test
    void 두_글자는_첫_글자만_남긴다() {
        assertThat(UserNameMasker.mask(user("김민"))).isEqualTo("김*");
    }

    @Test
    void 한_글자는_모두_가린다() {
        assertThat(UserNameMasker.mask(user("김"))).isEqualTo("*");
    }

    @Test
    void 이름이_없으면_익명으로_표시한다() {
        assertThat(UserNameMasker.mask(user(" "))).isEqualTo("익명");
    }

    @Test
    void 탈퇴했거나_사용자가_없으면_탈퇴회원으로_표시한다() {
        User withdrawn = User.builder()
                .name("박지훈")
                .status(UserStatus.WITHDRAWN)
                .build();

        assertThat(UserNameMasker.mask(withdrawn)).isEqualTo("탈퇴회원");
        assertThat(UserNameMasker.mask(null)).isEqualTo("탈퇴회원");
    }

    private User user(String name) {
        return User.builder()
                .name(name)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
