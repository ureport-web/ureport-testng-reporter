package io.ureport.testng.model;

/** Failure details attached to a test result. */
public class TestFailure {
    private String errorMessage;
    private String stackTrace;
    private String token;

    public TestFailure() {}

    public TestFailure(String errorMessage, String stackTrace) {
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
    }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
