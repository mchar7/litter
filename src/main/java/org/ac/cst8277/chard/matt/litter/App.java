package org.ac.cst8277.chard.matt.litter;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Main class for Litter application.
 * <p>
 * Slf4j annotation                         -> enables logging
 * Configuration annotation                 -> indicates that it's a configuration class
 * ConfigurationPropertiesScan annotation   -> enables configuration properties scanning
 * SpringBootApplication annotation         -> indicates that it's a Spring Boot app
 * EnableReactiveMongoRepositories          -> enables automagic reactive MongoDB repositories
 * OpenAPIDefinition annotation             -> provides OpenAPI metadata for the app
 */
@Slf4j
@Configuration
@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "org.ac.cst8277.chard.matt.litter.repository")
@OpenAPIDefinition(
        info = @Info(
                title = "Litter API",
                version = "0.0.1-SNAPSHOT",
                description = "Spring Boot API for Litter (a minimalist Twitter clone)"
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local server"),
                @Server(url = "https://dev.litter.dev", description = "Development server"),
                @Server(url = "https://staging.litter.dev", description = "Staging server"),
                @Server(url = "https://prod.litter.dev", description = "Production server")
        }
)
public class App {
    /**
     * Main method for the Litter application.
     *
     * @param args app startup arguments (takes none).
     */
    public static void main(String[] args) {
        log.info("Starting Litter application...");
        SpringApplication.run(App.class, args);
    }
}
