package com.example.senioron.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.FileSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .addServersItem(new Server().url("/"))
                .info(apiInfo());
    }

    @Bean
    public OpenApiCustomizer inquiryCreateMultipartCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null
                    || openApi.getPaths().get("/api/inquiries") == null
                    || openApi.getPaths().get("/api/inquiries").getPost() == null) {
                return;
            }

            io.swagger.v3.oas.models.media.MediaType multipartContent =
                    openApi.getPaths()
                            .get("/api/inquiries")
                            .getPost()
                            .getRequestBody()
                            .getContent()
                            .get(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE);

            if (multipartContent == null) {
                return;
            }

            Schema<?> requestSchema = new Schema<>()
                    .$ref("#/components/schemas/InquiryCreateRequest")
                    .description("문의 정보 JSON");

            Schema<?> imagesSchema = new ArraySchema()
                    .items(new FileSchema())
                    .description("문의 첨부 이미지 목록 (선택, 최대 5장)");

            multipartContent.schema(new ObjectSchema()
                    .addProperty("request", requestSchema)
                    .addProperty("images", imagesSchema)
                    .addRequiredItem("request")
            );
        };
    }

    private Info apiInfo() {
        return new Info()
                .title("SeniorON - Swagger")
                .description("SeniorON 프로젝트 API 명세서입니다.")
                .version("1.0.0");
    }
}
