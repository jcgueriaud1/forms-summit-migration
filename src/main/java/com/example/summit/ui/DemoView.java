package com.example.summit.ui;

import com.example.summit.presenter.DemoPresenter;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * The one demo view: proves the full spine wires together by displaying a single
 * datum read from Oracle through View &larr; Presenter &larr; Service &larr; Domain
 * &larr; Data. No Binder/Signals yet (ADR-0002) — there is nothing to edit.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Demo | Summit Migration POC")
public class DemoView extends VerticalLayout {

    public DemoView(DemoPresenter presenter) {
        Span value = new Span(presenter.databaseLabel());
        value.getStyle().set("font-size", "var(--lumo-font-size-xl)");
        value.getStyle().set("font-weight", "bold");

        add(new Paragraph("Connected database (read through View ← Presenter ← Service ← Domain ← Data):"),
                value);
    }
}
