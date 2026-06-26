package com.example.summit.presenter;

import com.example.summit.domain.ServerInfo;
import com.example.summit.domain.ServerInfoFormula;
import com.example.summit.service.DemoService;
import org.springframework.stereotype.Component;

/**
 * Presenter layer — owns <em>orchestration</em>, not the formula. It pulls from the
 * Service, applies a Domain formula, and returns a view-ready value. For the real
 * POC this is where Grid&nbsp;Pro cell-commit listeners call {@code recalculateTotal()}
 * and push results to the view (ADR-0001/0002). Here it is a single load+label.
 */
@Component
public class DemoPresenter {

    private final DemoService demoService;

    public DemoPresenter(DemoService demoService) {
        this.demoService = demoService;
    }

    /** Loads the server info and returns the display label for the view. */
    public String databaseLabel() {
        ServerInfo info = demoService.loadServerInfo();
        return ServerInfoFormula.label(info);
    }
}
