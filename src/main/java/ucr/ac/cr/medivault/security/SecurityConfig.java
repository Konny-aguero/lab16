package ucr.ac.cr.medivault.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwt,
                                           RateLimitingFilter rate, ProblemResponseWriter problems) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/h2-console/**").denyAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problems.write(req, res,
                                HttpStatus.UNAUTHORIZED, "Autenticacion requerida."))
                        .accessDeniedHandler((req, res, ex) -> problems.write(req, res,
                                HttpStatus.FORBIDDEN, "Acceso no autorizado.")))
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .cacheControl(c -> {})
                        .contentSecurityPolicy(c -> c.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none';"))
                        .httpStrictTransportSecurity(s -> s.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rate, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateRegistration(RateLimitingFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
