package io.ureport.testng;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.xml.XmlSuite;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TestNG listener that ships test results to UReport.
 *
 * <p>Implements both {@link IReporter} (for final report generation) and {@link ITestListener}
 * (to capture per-test step data and retry metadata on the correct thread).
 *
 * <p>Register in {@code testng.xml}:
 * <pre>{@code
 * <listeners>
 *   <listener class-name="io.ureport.testng.UReportListener"/>
 * </listeners>
 * }</pre>
 */
public class UReportListener implements IReporter, ITestListener {

    private static final DateTimeFormatter ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    /**
     * Steps captured from {@link UReportSteps} immediately after each test runs.
     * ConcurrentHashMap because ITestListener callbacks may come from multiple threads.
     */
    private final Map<ITestResult, List<UReportSteps.StepRecord>> stepsByResult =
            new ConcurrentHashMap<>();

    /**
     * Keys that had at least one failure callback — populated by {@link #onTestFailure} and
     * by {@link #onTestSkipped} for retried failures (TestNG fires onTestSkipped when a failing
     * test will be retried, changing its status to SKIP before re-running it).
     */
    private final Set<String> listenerFailedKeys = ConcurrentHashMap.newKeySet();

    /**
     * Keys that had at least one success callback — populated by {@link #onTestSuccess}.
     * The intersection of {@code listenerFailedKeys ∩ listenerPassedKeys} identifies methods
     * that failed at least once and eventually passed → retry scenario.
     */
    private final Set<String> listenerPassedKeys = ConcurrentHashMap.newKeySet();

    // -------------------------------------------------------------------------
    // ITestListener — capture steps on the test thread before the next test clears them
    // -------------------------------------------------------------------------

    @Override
    public void onTestSuccess(ITestResult result) {
        captureSteps(result);
        listenerPassedKeys.add(resultKey(result));
    }

    @Override
    public void onTestFailure(ITestResult result) {
        captureSteps(result);
        listenerFailedKeys.add(resultKey(result));
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        captureSteps(result);
        // TestNG changes a retrying failure's status to SKIP and fires onTestSkipped.
        // Detect this: retry analyzer set + throwable present = retried failure, not a real skip.
        if (result.getMethod().getRetryAnalyzerClass() != null && result.getThrowable() != null) {
            listenerFailedKeys.add(resultKey(result));
        }
    }

    private void captureSteps(ITestResult result) {
        stepsByResult.put(result, UReportSteps.consumeSteps());
    }

    // -------------------------------------------------------------------------
    // IReporter — called after all suites complete
    // -------------------------------------------------------------------------

    @Override
    public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String outputDir) {
        UReportConfig config;
        try {
            config = new UReportConfig();
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            return;
        }

        UReportApiClient client = new UReportApiClient(config.serverUrl, config.apiToken);

        // Methods that had at least one failure AND at least one success → retried
        // Built from ITestListener callbacks which fire for every attempt.
        Set<String> retriedMethodKeys = new HashSet<>(listenerFailedKeys);
        retriedMethodKeys.retainAll(listenerPassedKeys);

        // Collect all results and configuration results
        List<ITestResult> allResults = new ArrayList<>();
        List<ITestResult> configResults = new ArrayList<>();

        for (ISuite suite : suites) {
            for (ISuiteResult sr : suite.getResults().values()) {
                ITestContext ctx = sr.getTestContext();

                for (ITestResult r : ctx.getPassedTests().getAllResults()) {
                    allResults.add(r);
                }
                for (ITestResult r : ctx.getFailedTests().getAllResults()) {
                    allResults.add(r);
                    // Also detect retried failures still present in the failed set
                    if (r.wasRetried()) {
                        retriedMethodKeys.add(resultKey(r));
                    }
                }
                for (ITestResult r : ctx.getSkippedTests().getAllResults()) {
                    allResults.add(r);
                    // Intermediate retried failures: TestNG marks them SKIP and puts them here.
                    // wasRetried()=true means this attempt was re-run → belongs in retriedMethodKeys.
                    if (r.wasRetried()) {
                        retriedMethodKeys.add(resultKey(r));
                    }
                }

                for (ITestResult r : ctx.getPassedConfigurations().getAllResults()) {
                    configResults.add(r);
                }
                for (ITestResult r : ctx.getFailedConfigurations().getAllResults()) {
                    configResults.add(r);
                }
            }
        }

        // Build start time — earliest test start, or now
        long buildStart = allResults.stream()
                .mapToLong(ITestResult::getStartMillis)
                .filter(t -> t > 0)
                .min()
                .orElse(System.currentTimeMillis());

        // Create build
        String buildId;
        try {
            buildId = client.createBuild(buildBuildPayload(config, buildStart));
        } catch (Exception e) {
            System.err.println("[ureport-testng-reporter] Failed to create build: " + e.getMessage());
            return;
        }

        // Build test payloads
        List<Map<String, Object>> testPayloads = new ArrayList<>();
        Set<String> seenUids = new HashSet<>();
        List<Map<String, Object>> relations = new ArrayList<>();

        for (ITestResult result : allResults) {
            Map<String, Object> payload =
                    buildTestPayload(result, buildId, config, configResults, retriedMethodKeys);
            testPayloads.add(payload);

            String uid = (String) payload.get("uid");
            if (config.saveRelations && seenUids.add(uid)) {
                relations.add(buildRelationPayload(uid, result, config));
            }
        }

        // Disabled tests (@Test(enabled=false)) never fire listener callbacks and never appear in
        // passed/failed/skipped collections; find them via ctx.getExcludedMethods().
        Set<String> seenResultKeys = new HashSet<>();
        for (ITestResult r : allResults) seenResultKeys.add(resultKey(r));

        for (ISuite suite : suites) {
            for (ISuiteResult sr : suite.getResults().values()) {
                ITestContext ctx = sr.getTestContext();
                for (ITestNGMethod m : ctx.getExcludedMethods()) {
                    if (!m.isTest() || m.getEnabled()) continue; // only @Test(enabled=false)
                    String key = m.getTestClass().getName() + "#" + m.getMethodName();
                    if (!seenResultKeys.add(key)) continue;

                    Map<String, Object> payload = buildDisabledTestPayload(m, buildId, config);
                    testPayloads.add(payload);

                    String uid = (String) payload.get("uid");
                    if (config.saveRelations && seenUids.add(uid)) {
                        relations.add(buildDisabledRelationPayload(uid, m, config));
                    }
                }
            }
        }

        // Submit in batches
        try {
            for (int i = 0; i < testPayloads.size(); i += config.batchSize) {
                List<Map<String, Object>> batch =
                        testPayloads.subList(i, Math.min(i + config.batchSize, testPayloads.size()));
                client.submitTests(batch);
            }
        } catch (Exception e) {
            System.err.println("[ureport-testng-reporter] Failed to submit tests: " + e.getMessage());
        }

        // Finalize
        try {
            client.finalizeBuild(buildId);
        } catch (Exception e) {
            System.err.println("[ureport-testng-reporter] Failed to finalize build: " + e.getMessage());
        }

        // Save relations
        if (config.saveRelations) {
            for (Map<String, Object> relation : relations) {
                try {
                    client.saveRelation(relation);
                } catch (Exception e) {
                    System.err.println("[ureport-testng-reporter] Failed to save relation: " + e.getMessage());
                }
            }
        }

        // Output file
        if (config.outputFile != null) {
            writeOutputFile(config.outputFile, buildId, testPayloads, relations);
        }
    }

    // -------------------------------------------------------------------------
    // Payload builders
    // -------------------------------------------------------------------------

    private Map<String, Object> buildBuildPayload(UReportConfig config, long buildStart) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", config.product);
        payload.put("type", config.type);
        payload.put("build", config.buildNumber);
        if (config.team != null) payload.put("team", config.team);
        if (config.browser != null) payload.put("browser", config.browser);
        if (config.device != null) payload.put("device", config.device);
        if (config.platform != null) payload.put("platform", config.platform);
        if (config.platformVersion != null) payload.put("platform_version", config.platformVersion);
        if (config.stage != null) payload.put("stage", config.stage);
        if (config.version != null) payload.put("version", config.version);
        payload.put("start_time", toIso(buildStart));
        return payload;
    }

    private Map<String, Object> buildTestPayload(
            ITestResult result,
            String buildId,
            UReportConfig config,
            List<ITestResult> configResults,
            Set<String> retriedMethodKeys) {

        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        UReport annotation = (method != null) ? method.getAnnotation(UReport.class) : null;

        String uid = (annotation != null && !annotation.uid().isEmpty())
                ? annotation.uid()
                : result.getTestClass().getName() + "#" + result.getMethod().getMethodName();

        String key = resultKey(result);
        String status;
        boolean isRerun;
        int testStatus = result.getStatus();

        if (testStatus == ITestResult.SUCCESS) {
            if (retriedMethodKeys.contains(key)) {
                status = "RERUN_PASS";
                isRerun = true;
            } else {
                status = "PASS";
                isRerun = false;
            }
        } else if (testStatus == ITestResult.FAILURE
                || testStatus == ITestResult.SUCCESS_PERCENTAGE_FAILURE) {
            status = "FAIL";
            isRerun = retriedMethodKeys.contains(key);
        } else if (testStatus == ITestResult.SKIP
                && result.wasRetried()
                && result.getThrowable() != null) {
            // Retried failure: TestNG changes the status to SKIP when re-running a failed test.
            // Treat as FAIL; mark is_rerun if the method key is known to be retried.
            status = "FAIL";
            isRerun = retriedMethodKeys.contains(key);
        } else {
            status = "SKIP";
            isRerun = false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", uid);
        payload.put("name", result.getMethod().getMethodName());
        payload.put("build", buildId);
        payload.put("status", status);
        payload.put("start_time", toIso(result.getStartMillis()));
        payload.put("end_time", toIso(result.getEndMillis() > 0
                ? result.getEndMillis() : System.currentTimeMillis()));
        payload.put("is_rerun", isRerun);

        // Failure info
        if (result.getThrowable() != null) {
            Throwable t = result.getThrowable();
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("error_message", t.getMessage() != null ? t.getMessage() : t.toString());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            failure.put("stack_trace", sw.toString());
            payload.put("failure", failure);
        }

        // Info block
        payload.put("info", buildInfo(result, annotation));

        // Steps
        if (config.includeSteps) {
            List<Map<String, Object>> setup = getConfigSteps(result, configResults, true);
            List<Map<String, Object>> teardown = getConfigSteps(result, configResults, false);
            List<Map<String, Object>> body = getBodySteps(result);
            if (!setup.isEmpty()) payload.put("setup", setup);
            if (!body.isEmpty()) payload.put("body", body);
            if (!teardown.isEmpty()) payload.put("teardown", teardown);
        }

        return payload;
    }

    private Map<String, Object> buildInfo(ITestResult result, UReport annotation) {
        Map<String, Object> info = new LinkedHashMap<>();

        // Tags: union of @UReport.tags + TestNG groups
        Set<String> tags = new LinkedHashSet<>();
        if (annotation != null) {
            for (String t : annotation.tags()) tags.add(t);
        }
        String[] groups = result.getMethod().getGroups();
        if (groups != null) {
            Collections.addAll(tags, groups);
        }
        if (!tags.isEmpty()) info.put("tags", new ArrayList<>(tags));

        if (annotation != null && annotation.components().length > 0) {
            info.put("components", Arrays.asList(annotation.components()));
        }
        if (annotation != null && annotation.teams().length > 0) {
            info.put("teams", Arrays.asList(annotation.teams()));
        }

        long duration = result.getEndMillis() - result.getStartMillis();
        info.put("duration", Math.max(duration, 0));

        return info;
    }

    private List<Map<String, Object>> getConfigSteps(
            ITestResult testResult, List<ITestResult> configResults, boolean before) {
        List<Map<String, Object>> steps = new ArrayList<>();
        String testClassName = testResult.getTestClass().getName();

        for (ITestResult cr : configResults) {
            boolean isRightType = before
                    ? cr.getMethod().isBeforeMethodConfiguration()
                    : cr.getMethod().isAfterMethodConfiguration();
            if (!isRightType) continue;
            if (!cr.getTestClass().getName().equals(testClassName)) continue;

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("timestamp", toIso(cr.getStartMillis()));
            step.put("status", cr.getStatus() == ITestResult.SUCCESS ? "PASS" : "FAIL");
            step.put("detail", cr.getMethod().getMethodName());
            steps.add(step);
        }
        return steps;
    }

    private List<Map<String, Object>> getBodySteps(ITestResult result) {
        List<UReportSteps.StepRecord> records =
                stepsByResult.getOrDefault(result, Collections.emptyList());
        List<Map<String, Object>> steps = new ArrayList<>();
        for (UReportSteps.StepRecord record : records) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("timestamp", toIso(record.startMillis));
            step.put("status", record.status);
            step.put("detail", record.title);
            steps.add(step);
        }
        return steps;
    }

    private Map<String, Object> buildRelationPayload(
            String uid, ITestResult result, UReportConfig config) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("uid", uid);
        rel.put("product", config.product);
        rel.put("type", config.type);

        Method method = result.getMethod().getConstructorOrMethod().getMethod();
        UReport annotation = (method != null) ? method.getAnnotation(UReport.class) : null;

        Set<String> tags = new LinkedHashSet<>();
        if (annotation != null) {
            for (String t : annotation.tags()) tags.add(t);
        }
        String[] groups = result.getMethod().getGroups();
        if (groups != null) Collections.addAll(tags, groups);
        if (!tags.isEmpty()) rel.put("tags", new ArrayList<>(tags));

        if (annotation != null && annotation.components().length > 0) {
            rel.put("components", Arrays.asList(annotation.components()));
        }
        if (annotation != null && annotation.teams().length > 0) {
            rel.put("teams", Arrays.asList(annotation.teams()));
        }

        return rel;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> buildDisabledTestPayload(
            ITestNGMethod m, String buildId, UReportConfig config) {
        Method method = m.getConstructorOrMethod().getMethod();
        UReport annotation = (method != null) ? method.getAnnotation(UReport.class) : null;
        String uid = (annotation != null && !annotation.uid().isEmpty())
                ? annotation.uid()
                : m.getTestClass().getName() + "#" + m.getMethodName();
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uid", uid);
        payload.put("name", m.getMethodName());
        payload.put("build", buildId);
        payload.put("status", "SKIP");
        payload.put("start_time", toIso(now));
        payload.put("end_time", toIso(now));
        payload.put("is_rerun", false);
        payload.put("info", buildDisabledInfo(m, annotation));
        return payload;
    }

    private Map<String, Object> buildDisabledInfo(ITestNGMethod m, UReport annotation) {
        Map<String, Object> info = new LinkedHashMap<>();
        Set<String> tags = new LinkedHashSet<>();
        if (annotation != null) for (String t : annotation.tags()) tags.add(t);
        String[] groups = m.getGroups();
        if (groups != null) Collections.addAll(tags, groups);
        if (!tags.isEmpty()) info.put("tags", new ArrayList<>(tags));
        if (annotation != null && annotation.components().length > 0)
            info.put("components", Arrays.asList(annotation.components()));
        if (annotation != null && annotation.teams().length > 0)
            info.put("teams", Arrays.asList(annotation.teams()));
        info.put("duration", 0L);
        return info;
    }

    private Map<String, Object> buildDisabledRelationPayload(
            String uid, ITestNGMethod m, UReportConfig config) {
        Map<String, Object> rel = new LinkedHashMap<>();
        rel.put("uid", uid);
        rel.put("product", config.product);
        rel.put("type", config.type);
        Method method = m.getConstructorOrMethod().getMethod();
        UReport annotation = (method != null) ? method.getAnnotation(UReport.class) : null;
        Set<String> tags = new LinkedHashSet<>();
        if (annotation != null) for (String t : annotation.tags()) tags.add(t);
        String[] groups = m.getGroups();
        if (groups != null) Collections.addAll(tags, groups);
        if (!tags.isEmpty()) rel.put("tags", new ArrayList<>(tags));
        if (annotation != null && annotation.components().length > 0)
            rel.put("components", Arrays.asList(annotation.components()));
        if (annotation != null && annotation.teams().length > 0)
            rel.put("teams", Arrays.asList(annotation.teams()));
        return rel;
    }

    private String resultKey(ITestResult result) {
        return result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
    }

    private String toIso(long millis) {
        return ISO.format(Instant.ofEpochMilli(millis));
    }

    private void writeOutputFile(
            String path,
            String buildId,
            List<Map<String, Object>> tests,
            List<Map<String, Object>> relations) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("build", buildId);
            output.put("tests", tests);
            output.put("relations", relations);
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), output);
            System.out.println("[ureport-testng-reporter] Results written to " + path);
        } catch (Exception e) {
            System.err.println("[ureport-testng-reporter] Failed to write output file: " + e.getMessage());
        }
    }
}
