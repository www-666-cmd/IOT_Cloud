package com.iot.service;

import com.iot.config.JwtUtil;
import com.iot.dto.LoginRequest;
import com.iot.dto.RegisterRequest;
import com.iot.model.User;
import com.iot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // In-memory verification code store: phone -> {code, timestamp}
    private final Map<String, String> codeStore = new ConcurrentHashMap<>();
    private final Map<String, Long> codeTimeStore = new ConcurrentHashMap<>();
    private static final long CODE_EXPIRE_MS = 5 * 60 * 1000; // 5 minutes

    public void sendVerificationCode(String phone) {
        String code = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
        codeStore.put(phone, code);
        codeTimeStore.put(phone, System.currentTimeMillis());
        System.out.println("[验证码] 手机号: " + phone + " 验证码: " + code);
    }

    public String loginByPhone(String phone, String code) {
        String savedCode = codeStore.get(phone);
        Long savedTime = codeTimeStore.get(phone);
        if (savedCode == null || savedTime == null) {
            throw new RuntimeException("请先获取验证码");
        }
        if (System.currentTimeMillis() - savedTime > CODE_EXPIRE_MS) {
            codeStore.remove(phone);
            codeTimeStore.remove(phone);
            throw new RuntimeException("验证码已过期，请重新获取");
        }
        if (!savedCode.equals(code)) {
            throw new RuntimeException("验证码错误");
        }
        codeStore.remove(phone);
        codeTimeStore.remove(phone);

        User user = userRepository.findByPhone(phone).orElseGet(() -> {
            User newUser = User.builder()
                    .username("用户" + phone.substring(phone.length() - 4))
                    .email(phone + "@iot.com")
                    .phone(phone)
                    .password(passwordEncoder.encode(""))
                    .role("USER")
                    .build();
            return userRepository.save(newUser);
        });

        return jwtUtil.generateToken(user.getUsername());
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .build();
        return userRepository.save(user);
    }

    public String login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        return jwtUtil.generateToken(user.getUsername());
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public User getCurrentUserByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.emptyList()
        );
    }
}
