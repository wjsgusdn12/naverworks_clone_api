package com.jhwjhw.naverworkscloneapi.config;

import com.jhwjhw.naverworkscloneapi.auth.AppAuthProperties;
import com.jhwjhw.naverworkscloneapi.common.security.RestAccessDeniedHandler;
import com.jhwjhw.naverworkscloneapi.common.security.RestAuthenticationEntryPoint;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
// Security와 JWT 생성·검증에 필요한 Bean을 한 곳에서 구성.
@EnableConfigurationProperties(AppAuthProperties.class)
public class SecurityConfig {

    // SecurityFilterChain Bean 설정
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler) throws Exception {
        return http
                // JWT를 Authorization 헤더로 전달하므로 서버 세션을 만들지 않음.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        // 로그인 전에도 접근해야 하는 화면과 인증 시작 API.
                        .requestMatchers("/", "/error").permitAll()
                        // WebSocket 연결 인증은 STOMP CONNECT 단계에서 JWT로 검증.
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                // Filter 단계의 401·403도 Controller 예외 응답과 같은 JSON 형식으로 반환.
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                // 만료·위조된 Bearer JWT 오류도 Resource Server 기본 응답 대신 공통 JSON으로 반환.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    // JWT Encoder Bean 설정
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        // 같은 비밀키로 HS256 JWT 서명을 생성.
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    // JWT Decoder Bean 설정
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AppAuthProperties appAuthProperties) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // 서명뿐 아니라 발급자(issuer)와 만료 시간도 함께 검증.
        jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(appAuthProperties.issuer()));
        return jwtDecoder;
    }

    // JWT Secret Key Bean 설정
    @Bean
    public SecretKey jwtSecretKey(AppAuthProperties appAuthProperties) {
        // 환경 변수의 Base64 문자열을 실제 HMAC 서명 키 바이트로 복원.
        byte[] decodedSecret = Base64.getDecoder().decode(appAuthProperties.jwtSecretBase64());
        if (decodedSecret.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must contain at least 32 bytes.");
        }
        return new SecretKeySpec(decodedSecret, "HmacSHA256");
    }

    // JWT Authentication Converter Bean 설정
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractRoleAuthority);
        return converter;
    }

    // Password Encoder Bean 설정
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Role Authority 추출
    private Collection<GrantedAuthority> extractRoleAuthority(Jwt jwt) {
        // JWT의 ADMIN을 Spring Security가 이해하는 ROLE_ADMIN 형태로 변환.
        String role = jwt.getClaimAsString("role");
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    // Cors Configuration Source Bean 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // 리프레시 토큰 쿠키를 Next.js 요청에 포함하려면 credentials 허용이 필요.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
