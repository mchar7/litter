package org.ac.cst8277.chard.matt.litter;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Main application class for Litter - a Twitter-like social media platform.
 *
 * <p>This application provides a reactive REST API for managing messages, subscriptions,
 * and user accounts. It uses Spring WebFlux for non-blocking reactive endpoints,
 * MongoDB for data storage, and JWT for authentication.
 *
 * <p>The API includes:
 * <ul>
 *   <li>User management (registration, login)</li>
 *   <li>Message publishing and retrieval</li>
 *   <li>Subscription management between users</li>
 * </ul>
 */
@Slf4j
@Configuration
@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "org.ac.cst8277.chard.matt.litter.repository")
@OpenAPIDefinition(
        info = @Info(
                title = "Litter API",
                description = "Backend API for Litter. For more details, go to the [GitHub repository](https://github.com/mchar7/litter).",
                version = "v1", // this is the version of the API, not the application
                license = @License(name = "GPL-3.0", identifier = "GPL-3.0-or-later")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Local server"),
                @Server(url = "https://prod.litter.dev", description = "Production server")
        }
)
public class App {
    /**
     * Main method to start the Litter application.
     *
     * @param args Command line arguments (none required)
     */
    public static void main(String[] args) {
        log.info("Starting Litter application...");
        SpringApplication.run(App.class, args);
    }
}
