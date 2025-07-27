package com.lemillion.city_data_overload_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;


@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      // Disable CSRF on API endpoints so Postman can POST without a token
      .csrf(csrf -> csrf
        .ignoringRequestMatchers(new AntPathRequestMatcher("/api/**"))
      )
      .authorizeHttpRequests(auth -> auth
        // Allow all API calls through without authentication
        .requestMatchers("/api/**").permitAll()
        // Allow Swagger/OpenAPI documentation endpoints
        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()

        .requestMatchers("/admin/**").permitAll()

        // Everything else still needs login
        .anyRequest().authenticated()
      )
      // Keep form login for browser-based UI
      .formLogin(form -> form
        .permitAll()
        .defaultSuccessUrl("/", true)
      )
      .logout(logout -> logout.permitAll())
      // (Optional) enable HTTP Basic on APIs instead of form login
      // .httpBasic(Customizer.withDefaults())
      ;

    return http.build();
  }
}