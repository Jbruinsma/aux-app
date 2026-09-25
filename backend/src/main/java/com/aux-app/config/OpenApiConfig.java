package com.aux.config;

import java.util.Arrays;

import com.aux.auth.CurrentUser;
import com.aux.auth.OptionalCurrentUser;
import com.aux.dto.base.AuxServerError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Adds what springdoc can't infer: the Bearer scheme and the shared AuxServerError body on error responses.
// Endpoint-specific codes (404, 409, ...) go on the method as @ApiResponse(responseCode, description);
// the body is filled in here.
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenApiCustomizer bearerScheme() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.setComponents(new Components());
            openApi.getComponents().addSecuritySchemes(BEARER, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("Token from /api/auth/login or /api/auth/register"));
            ModelConverters.getInstance().readAll(AuxServerError.class)
                    .forEach(openApi.getComponents()::addSchemas);
        };
    }

    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handlerMethod) -> {
            var params = Arrays.asList(handlerMethod.getMethodParameters());
            boolean required = params.stream().anyMatch(p -> p.hasParameterAnnotation(CurrentUser.class));
            boolean optional = params.stream().anyMatch(p -> p.hasParameterAnnotation(OptionalCurrentUser.class));

            if (required) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
                operation.getResponses().addApiResponse("401", new ApiResponse()
                        .description("Missing or invalid token (code INVALID_TOKEN)"));
            } else if (optional) {
                // empty requirement = anonymous access also allowed
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER));
                operation.addSecurityItem(new SecurityRequirement());
            }
            if (operation.getRequestBody() != null) {
                operation.getResponses().addApiResponse("400", new ApiResponse()
                        .description("Invalid or malformed body (codes INVALID_FIELD, MALFORMED_BODY)"));
            }
            operation.getResponses().addApiResponse("500", new ApiResponse()
                    .description("Unexpected server error (code INTERNAL_ERROR)"));

            // every 4xx/5xx shares the AuxServerError body
            Schema<?> error = new Schema<>().$ref("#/components/schemas/AuxServerError");
            operation.getResponses().forEach((code, response) -> {
                if (code.startsWith("4") || code.startsWith("5")) {
                    response.setContent(new Content().addMediaType("application/json", new MediaType().schema(error)));
                }
            });
            return operation;
        };
    }
}
