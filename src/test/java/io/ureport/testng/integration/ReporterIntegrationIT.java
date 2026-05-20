package io.ureport.testng.integration;

import io.ureport.testng.UReportListener;
import io.ureport.testng.integration.fixtures.RetryTest;
import io.ureport.testng.integration.fixtures.SampleTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testng.ITestNGListener;
import org.testng.TestNG;
import org.testng.xml.XmlSuite;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UReportListener}. Runs TestNG programmatically against fixture
 * classes and asserts the HTTP calls captured by {@link MockUReportServer}.
 */
class ReporterIntegrationIT {

    private MockUReportServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockUReportServer();
        System.setProperty("ureport.serverUrl", "http://127.0.0.1:" + server.port());
        System.setProperty("ureport.apiToken", "test-token");
        System.setProperty("ureport.product", "TestProduct");
        System.setProperty("ureport.type", "unit");
    }

    @AfterEach
    void stopServer() {
        server.stop();
        System.clearProperty("ureport.serverUrl");
        System.clearProperty("ureport.apiToken");
        System.clearProperty("ureport.product");
        System.clearProperty("ureport.type");
        System.clearProperty("ureport.saveRelations");
        System.clearProperty("ureport.batchSize");
        System.clearProperty("ureport.outputFile");
        RetryTest.ATTEMPTS.set(0);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Default config — SampleTest
    // -------------------------------------------------------------------------

    @Test
    void defaultConfig_submitsCorrectStatuses() {
        runTestNG(SampleTest.class);

        List<Map<String, Object>> tests = server.getAllSubmittedTests();
        assertEquals(4, tests.size());

        Map<String, Object> passing = findByUid(tests, "TC-PASS-001");
        assertNotNull(passing, "TC-PASS-001 not found");
        assertEquals("PASS", passing.get("status"));
        assertFalse((Boolean) passing.get("is_rerun"));

        Map<String, Object> failing = findByMethodName(tests, "failingTest");
        assertNotNull(failing, "failingTest not found");
        assertEquals("FAIL", failing.get("status"));

        Map<String, Object> skipped = findByMethodName(tests, "skippedTest");
        assertNotNull(skipped, "skippedTest not found");
        assertEquals("SKIP", skipped.get("status"));

        Map<String, Object> noAnnotation = findByMethodName(tests, "noAnnotationTest");
        assertNotNull(noAnnotation, "noAnnotationTest not found");
        assertEquals("PASS", noAnnotation.get("status"));
        // uid should be className#methodName
        String expectedUid = SampleTest.class.getName() + "#noAnnotationTest";
        assertEquals(expectedUid, noAnnotation.get("uid"));
    }

    @Test
    void defaultConfig_buildCreatedAndFinalized() {
        runTestNG(SampleTest.class);

        assertEquals(1, server.getBuildRequests().size());
        Map<String, Object> buildReq = server.getBuildRequests().get(0);
        assertEquals("TestProduct", buildReq.get("product"));
        assertEquals("unit", buildReq.get("type"));

        assertEquals(1, server.getFinalizeRequests().size());
        assertTrue(server.getFinalizeRequests().get(0).contains("/calculate/mock-build-id"));
    }

    @Test
    void defaultConfig_annotationMetadataIncluded() {
        runTestNG(SampleTest.class);

        Map<String, Object> test = findByUid(server.getAllSubmittedTests(), "TC-PASS-001");
        assertNotNull(test);

        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) test.get("info");
        assertNotNull(info);

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) info.get("tags");
        assertNotNull(tags);
        assertTrue(tags.contains("smoke"), "tags should contain @UReport tag 'smoke'");

        @SuppressWarnings("unchecked")
        List<String> components = (List<String>) info.get("components");
        assertNotNull(components);
        assertTrue(components.contains("Home"));

        @SuppressWarnings("unchecked")
        List<String> teams = (List<String>) info.get("teams");
        assertNotNull(teams);
        assertTrue(teams.contains("frontend"));
    }

    @Test
    void defaultConfig_bodyStepsIncluded() {
        runTestNG(SampleTest.class);

        Map<String, Object> test = findByUid(server.getAllSubmittedTests(), "TC-PASS-001");
        assertNotNull(test);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) test.get("body");
        assertNotNull(body, "body steps should be present");
        assertEquals(1, body.size());
        assertEquals("Click login button", body.get(0).get("detail"));
        assertEquals("PASS", body.get(0).get("status"));
    }

    @Test
    void defaultConfig_setupAndTeardownStepsIncluded() {
        runTestNG(SampleTest.class);

        // passingTest has setUp + tearDown
        Map<String, Object> test = findByUid(server.getAllSubmittedTests(), "TC-PASS-001");
        assertNotNull(test);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> setup = (List<Map<String, Object>>) test.get("setup");
        assertNotNull(setup, "setup steps should be present");
        assertEquals("setUp", setup.get(0).get("detail"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teardown = (List<Map<String, Object>>) test.get("teardown");
        assertNotNull(teardown, "teardown steps should be present");
        assertEquals("tearDown", teardown.get(0).get("detail"));
    }

    @Test
    void defaultConfig_relationsCreated() {
        runTestNG(SampleTest.class);

        List<Map<String, Object>> relations = server.getRelationRequests();
        // 4 unique tests → 4 relations
        assertEquals(4, relations.size());

        Map<String, Object> rel = relations.stream()
                .filter(r -> "TC-PASS-001".equals(r.get("uid")))
                .findFirst()
                .orElse(null);
        assertNotNull(rel);
        assertEquals("TestProduct", rel.get("product"));
        assertEquals("unit", rel.get("type"));
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Retry — RERUN_PASS
    // -------------------------------------------------------------------------

    @Test
    void retry_flakeyTestIsRerunPass() {
        runTestNG(RetryTest.class);

        List<Map<String, Object>> tests = server.getAllSubmittedTests();
        // Should have at least one RERUN_PASS
        List<Map<String, Object>> reruns = tests.stream()
                .filter(t -> "RERUN_PASS".equals(t.get("status")))
                .collect(Collectors.toList());
        assertFalse(reruns.isEmpty(), "Expected at least one RERUN_PASS result");

        Map<String, Object> rerun = reruns.get(0);
        assertTrue((Boolean) rerun.get("is_rerun"));
    }

    // -------------------------------------------------------------------------
    // Scenario 3: saveRelations=false
    // -------------------------------------------------------------------------

    @Test
    void saveRelationsFalse_noRelationCalls() {
        System.setProperty("ureport.saveRelations", "false");
        runTestNG(SampleTest.class);
        assertTrue(server.getRelationRequests().isEmpty(), "No relation calls expected");
    }

    // -------------------------------------------------------------------------
    // Scenario 4: batchSize=2 with 4 tests → 2 POST /api/test/multi calls
    // -------------------------------------------------------------------------

    @Test
    void batchSize2_multiplePostCalls() {
        System.setProperty("ureport.batchSize", "2");
        runTestNG(SampleTest.class);

        List<Map<String, Object>> calls = server.getTestMultiRequests();
        assertEquals(2, calls.size(), "Expected 2 batched POST /api/test/multi calls");
    }

    // -------------------------------------------------------------------------
    // Scenario 5: outputFile — JSON written
    // -------------------------------------------------------------------------

    @Test
    void outputFile_jsonWritten() throws Exception {
        File tmp = File.createTempFile("ureport-test-", ".json");
        tmp.deleteOnExit();
        System.setProperty("ureport.outputFile", tmp.getAbsolutePath());

        runTestNG(SampleTest.class);

        String json = Files.readString(tmp.toPath());
        assertTrue(json.contains("mock-build-id"), "Output file should contain build id");
        assertTrue(json.contains("tests"), "Output file should contain tests array");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void runTestNG(Class<?>... classes) {
        UReportListener listener = new UReportListener();
        TestNG testng = new TestNG(false);
        testng.setTestClasses(classes);
        testng.addListener((ITestNGListener) listener);
        testng.run();
    }

    private Map<String, Object> findByUid(List<Map<String, Object>> tests, String uid) {
        return tests.stream().filter(t -> uid.equals(t.get("uid"))).findFirst().orElse(null);
    }

    private Map<String, Object> findByMethodName(List<Map<String, Object>> tests, String name) {
        return tests.stream().filter(t -> name.equals(t.get("name"))).findFirst().orElse(null);
    }
}
