package io.ureport.testng;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UReportConfigTest {

    @AfterEach
    void clearProps() {
        System.clearProperty("ureport.serverUrl");
        System.clearProperty("ureport.apiToken");
        System.clearProperty("ureport.product");
        System.clearProperty("ureport.type");
        System.clearProperty("ureport.buildNumber");
        System.clearProperty("ureport.batchSize");
        System.clearProperty("ureport.saveRelations");
        System.clearProperty("ureport.includeSteps");
        System.clearProperty("ureport.team");
        System.clearProperty("ureport.outputFile");
    }

    @Test
    void missingServerUrl_throwsIllegalState() {
        setRequiredExcept("ureport.serverUrl");
        IllegalStateException ex = assertThrows(IllegalStateException.class, UReportConfig::new);
        assertTrue(ex.getMessage().contains("ureport.serverUrl"));
    }

    @Test
    void missingApiToken_throwsIllegalState() {
        setRequiredExcept("ureport.apiToken");
        IllegalStateException ex = assertThrows(IllegalStateException.class, UReportConfig::new);
        assertTrue(ex.getMessage().contains("ureport.apiToken"));
    }

    @Test
    void missingProduct_throwsIllegalState() {
        setRequiredExcept("ureport.product");
        IllegalStateException ex = assertThrows(IllegalStateException.class, UReportConfig::new);
        assertTrue(ex.getMessage().contains("ureport.product"));
    }

    @Test
    void missingType_throwsIllegalState() {
        setRequiredExcept("ureport.type");
        IllegalStateException ex = assertThrows(IllegalStateException.class, UReportConfig::new);
        assertTrue(ex.getMessage().contains("ureport.type"));
    }

    @Test
    void allRequired_loadsSuccessfully() {
        setAllRequired();
        UReportConfig config = new UReportConfig();
        assertEquals("https://ureport.test", config.serverUrl);
        assertEquals("tok-123", config.apiToken);
        assertEquals("MyProduct", config.product);
        assertEquals("unit", config.type);
    }

    @Test
    void defaults_appliedForOptionalFields() {
        setAllRequired();
        UReportConfig config = new UReportConfig();
        assertEquals(50, config.batchSize);
        assertTrue(config.saveRelations);
        assertTrue(config.includeSteps);
        assertNull(config.team);
        assertNull(config.outputFile);
    }

    @Test
    void customBatchSize_parsed() {
        setAllRequired();
        System.setProperty("ureport.batchSize", "10");
        UReportConfig config = new UReportConfig();
        assertEquals(10, config.batchSize);
    }

    @Test
    void saveRelationsFalse_parsed() {
        setAllRequired();
        System.setProperty("ureport.saveRelations", "false");
        UReportConfig config = new UReportConfig();
        assertFalse(config.saveRelations);
    }

    @Test
    void includeStepsFalse_parsed() {
        setAllRequired();
        System.setProperty("ureport.includeSteps", "false");
        UReportConfig config = new UReportConfig();
        assertFalse(config.includeSteps);
    }

    @Test
    void customBuildNumber_parsed() {
        setAllRequired();
        System.setProperty("ureport.buildNumber", "42");
        UReportConfig config = new UReportConfig();
        assertEquals(42L, config.buildNumber);
    }

    @Test
    void optionalFields_loaded() {
        setAllRequired();
        System.setProperty("ureport.team", "backend");
        System.setProperty("ureport.browser", "chrome");
        System.setProperty("ureport.platform", "linux");
        UReportConfig config = new UReportConfig();
        assertEquals("backend", config.team);
        assertEquals("chrome", config.browser);
        assertEquals("linux", config.platform);
    }

    // -------------------------------------------------------------------------

    private void setAllRequired() {
        System.setProperty("ureport.serverUrl", "https://ureport.test");
        System.setProperty("ureport.apiToken", "tok-123");
        System.setProperty("ureport.product", "MyProduct");
        System.setProperty("ureport.type", "unit");
    }

    private void setRequiredExcept(String skip) {
        if (!skip.equals("ureport.serverUrl")) System.setProperty("ureport.serverUrl", "https://ureport.test");
        if (!skip.equals("ureport.apiToken")) System.setProperty("ureport.apiToken", "tok-123");
        if (!skip.equals("ureport.product")) System.setProperty("ureport.product", "MyProduct");
        if (!skip.equals("ureport.type")) System.setProperty("ureport.type", "unit");
    }
}
