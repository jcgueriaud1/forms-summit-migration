package com.example.summit;

import com.vaadin.flow.component.page.AppShellConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Implements {@link AppShellConfigurator} so Vaadin can
 * hang application-shell configuration (PWA, viewport, etc.) off it as the POC grows.
 */
@SpringBootApplication
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
