package com.example.summit;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point. Implements {@link AppShellConfigurator} so Vaadin can
 * hang application-shell configuration (PWA, viewport, etc.) off it as the POC grows.
 *
 * <p>The Aura theme owns all visual styling (typography, colour, spacing, borders);
 * see ADR-0008. It must be the first {@code @StyleSheet} so app stylesheets load after it.
 */
@StyleSheet(Aura.STYLESHEET)
@SpringBootApplication
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
