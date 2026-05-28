package io.ureport.testng;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.internal.ConstructorOrMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UReportListener helpers that don't require a live TestNG suite.
 * Private methods accessed via reflection; ITestResult/ITestNGMethod mocked with Mockito.
 */
class UReportListenerUnitTest {

    private UReportListener listener;
    private Method toIsoMethod;
    private Method resultKeyMethod;
    private Method buildBuildPayloadMethod;
    private Method buildTestPayloadMethod;

    // ── Annotated helper methods for annotation-parsing tests ─────────────────

    @UReport(uid = "TC-001", tags = {"smoke", "auth"}, components = {"Auth"}, teams = {"backend"})
    static void annotatedMethod() {}

    static void unannotatedMethod() {}

    // ── Setup / teardown ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        listener = new UReportListener();

        toIsoMethod = UReportListener.class.getDeclaredMethod("toIso", long.class);
        toIsoMethod.setAccessible(true);

        resultKeyMethod = UReportListener.class.getDeclaredMethod("resultKey", ITestResult.class);
        resultKeyMethod.setAccessible(true);

        buildBuildPayloadMethod = UReportListener.class.getDeclaredMethod(
                "buildBuildPayload", UReportConfig.class, long.class);
        buildBuildPayloadMethod.setAccessible(true);

        buildTestPayloadMethod = UReportListener.class.getDeclaredMethod(
                "buildTestPayload", ITestResult.class, String.class, UReportConfig.class,
                List.class, Set.class);
        buildTestPayloadMethod.setAccessible(true);

        setAllRequired();
    }

    @AfterEach
    void clearProps() {
        System.clearProperty("ureport.serverUrl");
        System.clearProperty("ureport.apiToken");
        System.clearProperty("ureport.product");
        System.clearProperty("ureport.type");
        System.clearProperty("ureport.buildNumber");
        System.clearProperty("ureport.team");
        System.clearProperty("ureport.browser");
        System.clearProperty("ureport.platform");
        System.clearProperty("ureport.stage");
        System.clearProperty("ureport.version");
    }

    // ── toIso ─────────────────────────────────────────────────────────────────

    @Test
    void toIso_formatsEpochZeroAsUtcIso() throws Exception {
        String result = (String) toIsoMethod.invoke(listener, 0L);
        assertEquals("1970-01-01T00:00:00.000Z", result);
    }

    @Test
    void toIso_formatsKnownTimestamp() throws Exception {
        // 2024-01-15T11:30:45.123Z = 1705318245123 ms
        long millis = 1705318245123L;
        String result = (String) toIsoMethod.invoke(listener, millis);
        assertEquals("2024-01-15T11:30:45.123Z", result);
    }

    @Test
    void toIso_alwaysEndsWithZ() throws Exception {
        String result = (String) toIsoMethod.invoke(listener, System.currentTimeMillis());
        assertTrue(result.endsWith("Z"), "ISO string must end with Z: " + result);
    }

    // ── resultKey ─────────────────────────────────────────────────────────────

    @Test
    void resultKey_returnsClassHashMethod() throws Exception {
        ITestResult result = mockResult("com.example.MyTest", "myMethod",
                ITestResult.SUCCESS, null);
        String key = (String) resultKeyMethod.invoke(listener, result);
        assertEquals("com.example.MyTest#myMethod", key);
    }

    // ── Status mapping ────────────────────────────────────────────────────────

    @Test
    void statusMapping_success_notRetried_isPass() throws Exception {
        UReportConfig config = new UReportConfig();
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.SUCCESS, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "build1", config, List.of(), Set.of());
        assertEquals("PASS", payload.get("status"));
        assertEquals(false, payload.get("is_rerun"));
    }

    @Test
    void statusMapping_success_inRetriedKeys_isRerunPass() throws Exception {
        UReportConfig config = new UReportConfig();
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.SUCCESS, null);
        Set<String> retried = new HashSet<>(Set.of("com.example.T#t1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "build1", config, List.of(), retried);
        assertEquals("RERUN_PASS", payload.get("status"));
        assertEquals(true, payload.get("is_rerun"));
    }

    @Test
    void statusMapping_failure_isFail() throws Exception {
        UReportConfig config = new UReportConfig();
        Throwable t = new AssertionError("expected true but was false");
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.FAILURE, t);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "build1", config, List.of(), Set.of());
        assertEquals("FAIL", payload.get("status"));
    }

    @Test
    void statusMapping_successPercentageFailure_isFail() throws Exception {
        UReportConfig config = new UReportConfig();
        ITestResult result = mockResult("com.example.T", "t1",
                ITestResult.SUCCESS_PERCENTAGE_FAILURE, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "build1", config, List.of(), Set.of());
        assertEquals("FAIL", payload.get("status"));
    }

    @Test
    void statusMapping_skip_isSkip() throws Exception {
        UReportConfig config = new UReportConfig();
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.SKIP, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "build1", config, List.of(), Set.of());
        assertEquals("SKIP", payload.get("status"));
        assertEquals(false, payload.get("is_rerun"));
    }

    // ── @UReport annotation parsing ───────────────────────────────────────────

    @Test
    void annotation_uidUsedWhenPresent() throws Exception {
        UReportConfig config = new UReportConfig();
        Method m = UReportListenerUnitTest.class.getDeclaredMethod("annotatedMethod");
        ITestResult result = mockResultWithMethod("com.example.T", "annotatedMethod",
                ITestResult.SUCCESS, null, m);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        assertEquals("TC-001", payload.get("uid"));
    }

    @Test
    void annotation_fallbackUidWhenNoAnnotation() throws Exception {
        UReportConfig config = new UReportConfig();
        Method m = UReportListenerUnitTest.class.getDeclaredMethod("unannotatedMethod");
        ITestResult result = mockResultWithMethod("com.example.T", "unannotatedMethod",
                ITestResult.SUCCESS, null, m);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        assertEquals("com.example.T#unannotatedMethod", payload.get("uid"));
    }

    @Test
    void annotation_tagsFromAnnotation() throws Exception {
        UReportConfig config = new UReportConfig();
        Method m = UReportListenerUnitTest.class.getDeclaredMethod("annotatedMethod");
        ITestResult result = mockResultWithMethod("com.example.T", "annotatedMethod",
                ITestResult.SUCCESS, null, m);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) payload.get("info");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) info.get("tags");
        assertTrue(tags.contains("smoke"));
        assertTrue(tags.contains("auth"));
    }

    @Test
    void annotation_tagsMergedWithTestNgGroups_deduped() throws Exception {
        UReportConfig config = new UReportConfig();
        Method m = UReportListenerUnitTest.class.getDeclaredMethod("annotatedMethod");
        // annotatedMethod has tags: smoke, auth
        // groups: smoke, regression  →  merged deduped: smoke, auth, regression
        ITestResult result = mockResultWithMethod("com.example.T", "annotatedMethod",
                ITestResult.SUCCESS, null, m, new String[]{"smoke", "regression"});
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) payload.get("info");
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) info.get("tags");
        assertEquals(3, tags.size(), "Expected 3 deduped tags but got: " + tags);
        assertTrue(tags.contains("smoke"));
        assertTrue(tags.contains("auth"));
        assertTrue(tags.contains("regression"));
    }

    @Test
    void annotation_componentsAndTeams() throws Exception {
        UReportConfig config = new UReportConfig();
        Method m = UReportListenerUnitTest.class.getDeclaredMethod("annotatedMethod");
        ITestResult result = mockResultWithMethod("com.example.T", "annotatedMethod",
                ITestResult.SUCCESS, null, m);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) payload.get("info");
        assertTrue(((List<?>) info.get("components")).contains("Auth"));
        assertTrue(((List<?>) info.get("teams")).contains("backend"));
    }

    // ── Failure info ──────────────────────────────────────────────────────────

    @Test
    void failureInfo_extractedWhenThrowablePresent() throws Exception {
        UReportConfig config = new UReportConfig();
        Throwable t = new AssertionError("expected 1 but got 2");
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.FAILURE, t);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) payload.get("failure");
        assertNotNull(failure, "failure block must be present");
        assertEquals("expected 1 but got 2", failure.get("error_message"));
        assertNotNull(failure.get("stack_trace"));
    }

    @Test
    void failureInfo_absentWhenNoThrowable() throws Exception {
        UReportConfig config = new UReportConfig();
        ITestResult result = mockResult("com.example.T", "t1", ITestResult.SUCCESS, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildTestPayloadMethod.invoke(
                listener, result, "b1", config, List.of(), Set.of());
        assertFalse(payload.containsKey("failure"), "failure block must be absent on success");
    }

    // ── buildBuildPayload ─────────────────────────────────────────────────────

    @Test
    void buildBuildPayload_requiredFieldsPresent() throws Exception {
        UReportConfig config = new UReportConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildBuildPayloadMethod.invoke(
                listener, config, System.currentTimeMillis());
        assertEquals("MyProduct", payload.get("product"));
        assertEquals("E2E", payload.get("type"));
        assertNotNull(payload.get("build"));
        assertNotNull(payload.get("start_time"));
    }

    @Test
    void buildBuildPayload_optionalOmittedWhenNull() throws Exception {
        UReportConfig config = new UReportConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildBuildPayloadMethod.invoke(
                listener, config, System.currentTimeMillis());
        assertFalse(payload.containsKey("team"),    "team must be absent when null");
        assertFalse(payload.containsKey("browser"), "browser must be absent when null");
        assertFalse(payload.containsKey("device"),  "device must be absent when null");
    }

    @Test
    void buildBuildPayload_optionalFieldsIncludedWhenSet() throws Exception {
        System.setProperty("ureport.team",    "backend");
        System.setProperty("ureport.browser", "chrome");
        System.setProperty("ureport.stage",   "staging");
        UReportConfig config = new UReportConfig();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) buildBuildPayloadMethod.invoke(
                listener, config, System.currentTimeMillis());
        assertEquals("backend", payload.get("team"));
        assertEquals("chrome",  payload.get("browser"));
        assertEquals("staging", payload.get("stage"));
    }

    // ── Retry detection ───────────────────────────────────────────────────────

    @Test
    void retryDetection_intersectionOfFailedAndPassed() throws Exception {
        // Simulate: t1 failed then passed (retry), t2 passed only
        ITestResult failResult1 = mockResult("com.T", "t1", ITestResult.FAILURE,
                new RuntimeException("fail"));
        ITestResult passResult1 = mockResult("com.T", "t1", ITestResult.SUCCESS, null);
        ITestResult passResult2 = mockResult("com.T", "t2", ITestResult.SUCCESS, null);

        listener.onTestFailure(failResult1);
        listener.onTestSuccess(passResult1);
        listener.onTestSuccess(passResult2);

        // Compute retried keys as generateReport does
        Field failedField = UReportListener.class.getDeclaredField("listenerFailedKeys");
        failedField.setAccessible(true);
        Field passedField = UReportListener.class.getDeclaredField("listenerPassedKeys");
        passedField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> failed = (Set<String>) failedField.get(listener);
        @SuppressWarnings("unchecked")
        Set<String> passed = (Set<String>) passedField.get(listener);
        Set<String> retried = new HashSet<>(failed);
        retried.retainAll(passed);

        assertTrue(retried.contains("com.T#t1"),   "t1 should be retried");
        assertFalse(retried.contains("com.T#t2"),  "t2 was never failed");
    }

    // ── Mock helpers ──────────────────────────────────────────────────────────

    private ITestResult mockResult(String className, String methodName, int status, Throwable t) {
        return mockResultWithMethod(className, methodName, status, t, null);
    }

    private ITestResult mockResultWithMethod(
            String className, String methodName, int status, Throwable t, Method method) {
        return mockResultWithMethod(className, methodName, status, t, method, new String[0]);
    }

    private ITestResult mockResultWithMethod(
            String className, String methodName, int status, Throwable t,
            Method reflectMethod, String[] groups) {
        ITestResult result = mock(ITestResult.class);
        ITestNGMethod ngMethod = mock(ITestNGMethod.class);
        org.testng.IClass iClass = mock(org.testng.IClass.class);
        ConstructorOrMethod com = mock(ConstructorOrMethod.class);

        when(result.getStatus()).thenReturn(status);
        when(result.getThrowable()).thenReturn(t);
        when(result.getStartMillis()).thenReturn(System.currentTimeMillis());
        when(result.getEndMillis()).thenReturn(System.currentTimeMillis() + 100);
        when(result.getMethod()).thenReturn(ngMethod);
        when(result.getTestClass()).thenReturn(iClass);

        when(ngMethod.getMethodName()).thenReturn(methodName);
        when(ngMethod.getGroups()).thenReturn(groups != null ? groups : new String[0]);
        when(ngMethod.getConstructorOrMethod()).thenReturn(com);
        when(com.getMethod()).thenReturn(reflectMethod);

        when(iClass.getName()).thenReturn(className);

        return result;
    }

    private void setAllRequired() {
        System.setProperty("ureport.serverUrl", "https://ureport.test");
        System.setProperty("ureport.apiToken",  "tok-123");
        System.setProperty("ureport.product",   "MyProduct");
        System.setProperty("ureport.type",      "E2E");
    }
}
