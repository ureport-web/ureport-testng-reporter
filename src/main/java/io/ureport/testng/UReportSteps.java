package io.ureport.testng;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe helper for recording named steps inside TestNG test methods.
 *
 * <pre>{@code
 * @Test
 * public void checkoutTest() throws Exception {
 *     UReportSteps.step("Add item to cart", () -> {
 *         // action + assertions
 *     });
 *     UReportSteps.step("Proceed to checkout", () -> {
 *         // action + assertions
 *     });
 * }
 * }</pre>
 *
 * <p>Steps are stored in a {@link ThreadLocal} list so parallel test execution is safe.
 * {@link UReportListener} calls {@link #consumeSteps()} immediately after each test completes
 * to drain the list before the next test on the same thread begins.
 */
public class UReportSteps {

    /** Runnable that may throw a checked exception. */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Internal record of a single recorded step. */
    public static class StepRecord {
        public final String title;
        public final long startMillis;
        public final String status;

        public StepRecord(String title, long startMillis, String status) {
            this.title = title;
            this.startMillis = startMillis;
            this.status = status;
        }
    }

    private static final ThreadLocal<List<StepRecord>> CURRENT_STEPS =
            ThreadLocal.withInitial(ArrayList::new);

    /**
     * Records a named step. If {@code action} throws, the step is marked {@code FAIL} and
     * the exception is re-thrown so TestNG can mark the test as failed.
     */
    public static void step(String title, ThrowingRunnable action) throws Exception {
        long start = System.currentTimeMillis();
        String status = "PASS";
        try {
            action.run();
        } catch (Exception e) {
            status = "FAIL";
            throw e;
        } finally {
            CURRENT_STEPS.get().add(new StepRecord(title, start, status));
        }
    }

    /**
     * Drains and returns all steps recorded on the current thread since the last call.
     * Clears the ThreadLocal to prevent leaks between tests sharing the same thread.
     */
    public static List<StepRecord> consumeSteps() {
        List<StepRecord> steps = new ArrayList<>(CURRENT_STEPS.get());
        CURRENT_STEPS.remove();
        return steps;
    }
}
