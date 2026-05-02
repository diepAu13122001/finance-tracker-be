package com.diepau1312.financeTrackerBE.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  @Bean
  public OpenAPI openAPI() {
    // Tên scheme cho JWT
    final String securitySchemeName = "bearerAuth";

    return new OpenAPI()
        .info(new Info()
            .title("Finance Tracker API")
            .description("API quản lý tài chính cá nhân — 3 gói Free/Plus/Premium")
            .version("1.0.0")
            .contact(new Contact()
                .name("Diệp Âu")
                .url("https://github.com/diepau13122001")))
        // Thêm JWT auth vào tất cả endpoints
        .addSecurityItem(new SecurityRequirement()
            .addList(securitySchemeName))
        .components(new Components()
            .addSecuritySchemes(securitySchemeName,
                new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Nhập JWT token từ /api/auth/login")));
  }
}