package com.example.senioron.domain.user.service;

import com.example.senioron.domain.user.dto.request.UserLoginRequest;
import com.example.senioron.domain.user.dto.request.UserSignUpRequest;
import com.example.senioron.domain.user.dto.response.LoginIdCheckResponse;
import com.example.senioron.domain.user.dto.response.UserLoginResponse;
import com.example.senioron.domain.user.dto.response.UserSignUpResponse;
import com.example.senioron.domain.user.entity.User;
import com.example.senioron.domain.user.entity.UserStatus;
import com.example.senioron.domain.user.repository.UserRepository;
import com.example.senioron.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 아이디 중복 확인 서비스
    public LoginIdCheckResponse checkLoginId(String loginId) {
        boolean exists = userRepository.existsByLoginId(loginId);

        return LoginIdCheckResponse.builder()
                .isAvailable(!exists)
                .build();
    }


    // 최종 회원가입 서비스
    public UserSignUpResponse signUp(UserSignUpRequest request) {
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }

        if (!request.getPassword().equals(request.getPasswordCheck())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        User user = User.builder()
                .loginId(request.getLoginId())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .birth(request.getBirth())
                .status(UserStatus.ACTIVE)
                .build();

        User savedUser = userRepository.save(user);

        return UserSignUpResponse.builder()
                .usersId(savedUser.getUsersId())
                .name(savedUser.getName())
                .loginId(savedUser.getLoginId())
                .build();
    }


    // 로그인 서비스
    public UserLoginResponse login(UserLoginRequest request) {
        User user = userRepository.findByLoginId(request.getLoginId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtUtil.createAccessToken(user);

        return UserLoginResponse.builder()
                .usersId(user.getUsersId())
                .name(user.getName())
                .loginId(user.getLoginId())
                .accessToken(accessToken)
                .build();
    }

}