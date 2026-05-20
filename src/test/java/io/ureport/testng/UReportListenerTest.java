package io.ureport.testng;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UReportListenerTest {

    @Test
    void steps_capturedOnCurrentThread() throws Exception {
        // Record steps on this thread
        UReportSteps.step("Step A", () -> {});
        UReportSteps.step("Step B", () -> {});

        List<UReportSteps.StepRecord> steps = UReportSteps.consumeSteps();
        assertEquals(2, steps.size());
        assertEquals("Step A", steps.get(0).title);
        assertEquals("PASS", steps.get(0).status);
        assertEquals("Step B", steps.get(1).title);
    }

    @Test
    void failingStep_markedFail_exceptionRethrown() {
        assertThrows(RuntimeException.class, () -> {
            UReportSteps.step("Bad step", () -> { throw new RuntimeException("boom"); });
        });

        List<UReportSteps.StepRecord> steps = UReportSteps.consumeSteps();
        assertEquals(1, steps.size());
        assertEquals("FAIL", steps.get(0).status);
        assertEquals("Bad step", steps.get(0).title);
    }

    @Test
    void consumeSteps_clearsThreadLocal() throws Exception {
        UReportSteps.step("One", () -> {});
        UReportSteps.consumeSteps();

        List<UReportSteps.StepRecord> steps = UReportSteps.consumeSteps();
        assertTrue(steps.isEmpty(), "ThreadLocal should be cleared after consume");
    }

    @Test
    void steps_isolatedBetweenThreads() throws Exception {
        UReportSteps.step("Main thread step", () -> {});

        List<UReportSteps.StepRecord>[] threadSteps = new List[1];
        Thread other = new Thread(() -> {
            try {
                UReportSteps.step("Other thread step", () -> {});
            } catch (Exception ignored) {}
            threadSteps[0] = UReportSteps.consumeSteps();
        });
        other.start();
        other.join();

        List<UReportSteps.StepRecord> mainSteps = UReportSteps.consumeSteps();

        // Main thread only has its own step
        assertEquals(1, mainSteps.size());
        assertEquals("Main thread step", mainSteps.get(0).title);

        // Other thread only has its own step
        assertEquals(1, threadSteps[0].size());
        assertEquals("Other thread step", threadSteps[0].get(0).title);
    }

    @Test
    void stepRecord_storesStartTime() throws Exception {
        long before = System.currentTimeMillis();
        UReportSteps.step("Timed step", () -> {});
        long after = System.currentTimeMillis();

        List<UReportSteps.StepRecord> steps = UReportSteps.consumeSteps();
        assertTrue(steps.get(0).startMillis >= before);
        assertTrue(steps.get(0).startMillis <= after);
    }
}
