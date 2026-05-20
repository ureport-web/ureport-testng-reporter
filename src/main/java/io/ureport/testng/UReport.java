package io.ureport.testng;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches UReport metadata to a TestNG test method.
 *
 * <pre>{@code
 * @Test
 * @UReport(uid = "TC-LOGIN-001", tags = {"smoke", "auth"}, components = {"Auth"}, teams = {"backend"})
 * public void loginTest() { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UReport {
    /** Unique test case identifier. Defaults to className#methodName when empty. */
    String uid() default "";

    /** Tags to attach to this test (merged with TestNG groups). */
    String[] tags() default {};

    /** Component labels for this test. */
    String[] components() default {};

    /** Team labels for this test. */
    String[] teams() default {};
}
