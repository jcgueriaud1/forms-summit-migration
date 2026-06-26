package com.example.summit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Domain layer is pure — no Spring context, no database. This is the property the
 * layering buys us: every formula is unit-testable in isolation (ADR-0001).
 */
class ServerInfoFormulaTest {

    @Test
    void labelsProductAndVersion() {
        assertEquals("Oracle AI Database 26ai Free (23.26.2.0.0)",
                ServerInfoFormula.label(new ServerInfo("Oracle AI Database 26ai Free", "23.26.2.0.0")));
    }

    @Test
    void labelsProductOnlyWhenVersionMissing() {
        assertEquals("Oracle AI Database 26ai Free",
                ServerInfoFormula.label(new ServerInfo("Oracle AI Database 26ai Free", null)));
        assertEquals("Oracle AI Database 26ai Free",
                ServerInfoFormula.label(new ServerInfo("  Oracle AI Database 26ai Free  ", "   ")));
    }

    @Test
    void fallsBackOnMissingProduct() {
        assertEquals("Oracle Database (version unknown)",
                ServerInfoFormula.label(new ServerInfo(null, "23.26.2.0.0")));
        assertEquals("Oracle Database (version unknown)",
                ServerInfoFormula.label(null));
    }
}
