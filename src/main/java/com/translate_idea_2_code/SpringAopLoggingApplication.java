package com.translate_idea_2_code;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring AOP logging sample application.
 * <p>
 * This bootstrap class initializes the Spring Boot runtime and enables
 * component scanning for the logging aspects located in this project.
 */
@SpringBootApplication
public class SpringAopLoggingApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments passed to the JVM
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringAopLoggingApplication.class, args);
    }

}
