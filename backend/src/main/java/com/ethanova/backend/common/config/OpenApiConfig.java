package com.ethanova.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the auto-generated OpenAPI 3 spec.
 * SpringDoc supplies everything else from Spring MVC annotations.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ethanovaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ethanova API")
                        .version("v1")
                        .description(
                                "REST API for Ethanova — an Enterprise Decision Intelligence Platform " +
                                "for India's E20 Biofuel Supply Chain.")
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}