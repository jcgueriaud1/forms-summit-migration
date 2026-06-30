package com.example.summit.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

/**
 * Application shell. An {@link AppLayout} so the master-detail and other migration
 * views drop into a consistent frame as the POC grows.
 */
public class MainLayout extends AppLayout {

    public MainLayout() {
        H1 title = new H1("Summit Migration POC");
        // Aura owns typography and spacing (ADR-0008); style via theme tokens, not
        // Lumo utility classes (which require the Lumo theme).
        title.getStyle()
                .set("font-size", "var(--aura-font-size-l)")
                .set("margin", "var(--vaadin-gap)");

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(), title);
        header.setDefaultVerticalComponentAlignment(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);
        header.setWidthFull();

        addToNavbar(header);
    }
}
