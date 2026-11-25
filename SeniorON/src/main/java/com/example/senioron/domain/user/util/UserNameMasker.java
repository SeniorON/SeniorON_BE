package com.example.senioron.domain.user.util;

import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;

public final class UserNameMasker {

    private static final String WITHDRAWN_NAME = "탈퇴회원";

    private UserNameMasker() {
    }

    public static String mask(User user) {
        if (user == null) {
            return WITHDRAWN_NAME;
        }

        return mask(user.getName(), user.getStatus());
    }

    public static String mask(String name, UserStatus status) {
        if (status == UserStatus.WITHDRAWN) {
            return WITHDRAWN_NAME;
        }

        if (name == null || name.isBlank()) {
            return "익명";
        }

        int length = name.codePointCount(0, name.length());

        if (length == 1) {
            return "*";
        }

        String first = name.substring(0, name.offsetByCodePoints(0, 1));

        if (length == 2) {
            return first + "*";
        }

        String last = name.substring(name.offsetByCodePoints(0, length - 1));

        return first + "*" + last;
    }
}
