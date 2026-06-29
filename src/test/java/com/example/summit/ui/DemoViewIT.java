package com.example.summit.ui;

import com.example.summit.it.support.OracleSpringPlaywrightIT;
import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * UI integration test for {@link DemoView} — the scaffold's one view, mapped at the root
 * route. Drives the running app in a real browser (Playwright + DramaFinder) against a
 * real Oracle container and asserts the spine wired up: the page renders and the database
 * banner read through View &larr; Presenter &larr; Service &larr; Domain &larr; Data shows.
 *
 * <p>{@code DemoView} renders only {@code Paragraph}/{@code Span} (no Vaadin form
 * components), so there is no DramaFinder wrapper to use — these assertions go through
 * user-visible text with Playwright page locators, which is the sanctioned fallback for
 * components that have no wrapper.
 */
public class DemoViewIT extends OracleSpringPlaywrightIT {

    // DemoView is at @Route(value = "") — the root path. getUrl() already ends in "/",
    // and getView() defaults to "", so no override is needed to land on the view.

    @Test
    public void testTitle() {
        assertThat(page).hasTitle("Demo | Summit Migration POC");
    }

    @Test
    public void testIntroParagraphIsVisible() {
        Locator intro = page.getByText(Pattern.compile("Connected database \\(read through"));
        assertThat(intro).isVisible();
    }

    @Test
    public void testDatabaseLabelShowsOracleBanner() {
        // The label is "<product> (<version>)" with product LIKE '%Database%' — the
        // parenthesised version is what distinguishes the bold banner span from the
        // intro paragraph above it.
        Locator banner = page.locator("span")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("Database.*\\(.+\\)")));
        assertThat(banner).isVisible();
    }
}
