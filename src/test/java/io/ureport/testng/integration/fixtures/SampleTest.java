package io.ureport.testng.integration.fixtures;

import io.ureport.testng.UReport;
import io.ureport.testng.UReportSteps;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * TestNG fixture used by integration tests. Covers pass, fail, skip, and no-annotation paths.
 */
public class SampleTest {

    @BeforeMethod
    public void setUp() {
        // captured as setup step
    }

    @AfterMethod
    public void tearDown() {
        // captured as teardown step
    }

    @Test(description = "TC-PASS-001", groups = "smoke")
    @UReport(uid = "TC-PASS-001", tags = {"smoke"}, components = {"Home"}, teams = {"frontend"})
    public void passingTest() throws Exception {
        UReportSteps.step("Click login button", () -> {
            // no-op assertion
        });
    }

    @Test(description = "TC-FAIL-001")
    public void failingTest() {
        Assert.fail("intentional failure");
    }

    @Test(dependsOnMethods = "failingTest")
    public void skippedTest() {
        // will be skipped because failingTest fails
    }

    @Test
    public void noAnnotationTest() {
        // uid defaults to className#methodName
    }
}
