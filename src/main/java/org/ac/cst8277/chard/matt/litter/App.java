package org.ac.cst8277.chard.matt.litter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

/**
 * Main class for Litter application.
 * ConfigurationPropertiesScan annotation   -> enables configuration properties scanning.
 * SpringBootApplication annotation         -> indicates that it's a Spring Boot app.
 * EnableReactiveMongoRepositories          -> enables automagic reactive MongoDB repositories.
 */
@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "org.ac.cst8277.chard.matt.litter.repository")
public class App {

    /**
     * Logger instance for the App class.
     */
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    /**
     * Main method for the Litter application.
     *
     * @param args app startup arguments (takes none).
     */
    public static void main(String[] args) {
        logger.info("Starting Litter application...");
        SpringApplication.run(App.class, args);
    }
}