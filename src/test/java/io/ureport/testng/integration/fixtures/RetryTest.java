package io.ureport.testng.integration.fixtures;

import org.testng.Assert;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * TestNG fixture that retries once. First attempt fails, second passes.
 * Used to verify RERUN_PASS status mapping.
 */
public class RetryTest {

    /** Shared counter so the test body can decide whether to fail based on attempt number. */
    public static final AtomicInteger ATTEMPTS = new AtomicInteger(0);

    @Test(retryAnalyzer = RetryTest.Retry.class)
    public void flakeyTest() {
        int attempt = ATTEMPTS.getAndIncrement();
        if (attempt == 0) {
            Assert.fail("First attempt fails — retry expected");
        }
        // Second attempt passes
    }

    public static class Retry implements IRetryAnalyzer {
        private int count = 0;

        @Override
        public boolean retry(ITestResult result) {
            if (count < 1) {
                count++;
                return true;
            }
            return false;
        }
    }
}
