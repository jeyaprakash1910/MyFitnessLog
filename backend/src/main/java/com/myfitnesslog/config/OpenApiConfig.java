package com.myfitnesslog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the MyFitnessLog REST API.
 * Sets only the documented title, description, and version.
 * Endpoint discovery and Swagger UI are provided automatically by SpringDoc.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI myFitnessLogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyFitnessLog API")
                        .description("MyFitnessLog is a personal fitness tracking application built to provide a fast, reliable, and distraction-free workout logging experience.")
                        .version("1.0"));
    }
}
