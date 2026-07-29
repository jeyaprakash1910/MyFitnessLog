package com.myfitnesslog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Authentication is the API-key filter chain only (SecurityConfig / ApiKeyAuthFilter).
// Excluding UserDetailsServiceAutoConfiguration stops Spring Boot creating an unused
// in-memory user and logging a generated password at startup — the auth path stays
// exactly what SecurityConfig defines, with no latent Basic/form-login credential.
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class MyFitnessLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyFitnessLogApplication.class, args);
    }

}
