package io.ureport.testng.model;

import java.util.List;

/** Represents a single step in a test's setup, body, or teardown phase. */
public class StepPayload {
    private String timestamp;
    private String status;
    private String detail;
    private List<StepPayload> steps;

    public StepPayload() {}

    public StepPayload(String timestamp, String status, String detail) {
        this.timestamp = timestamp;
        this.status = status;
        this.detail = detail;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public List<StepPayload> getSteps() { return steps; }
    public void setSteps(List<StepPayload> steps) { this.steps = steps; }
}
