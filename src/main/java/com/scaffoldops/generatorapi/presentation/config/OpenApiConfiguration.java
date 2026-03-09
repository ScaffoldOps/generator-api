package com.scaffoldops.generatorapi.presentation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI generatorApiOpenApi(
            @Value("${info.app.title}") String title,
            @Value("${info.app.description}") String description,
            @Value("${info.app.version}") String version,
            @Value("${spring.mvc.servlet.path}") String apiBasePath
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version))
                .addServersItem(new Server()
                        .url(apiBasePath)
                        .description("Base API path"));
    }
}
