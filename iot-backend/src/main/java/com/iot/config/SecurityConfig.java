package com.iot.config;
// Spring Security 的核心配置类，作用是：定义整个系统的安全策略（认证 + 授权 + 请求过滤链 + 无状态会话模型），同时集成 JWT 和设备 API Key 双认证机制。
import com.iot.repository.DeviceRepository;
import com.iot.repository.UserRepository;
import com.iot.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final AuthService authService;
    private final CorsConfigurationSource corsConfigurationSource;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    public SecurityConfig(JwtUtil jwtUtil, @Lazy AuthService authService,
                          CorsConfigurationSource corsConfigurationSource,
                          DeviceRepository deviceRepository,
                          UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.authService = authService;
        this.corsConfigurationSource = corsConfigurationSource;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtUtil, authService);
    }

    @Bean
    public DeviceApiKeyFilter deviceApiKeyFilter() {
        return new DeviceApiKeyFilter(deviceRepository, userRepository);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .addFilterBefore(deviceApiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
