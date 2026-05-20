package io.ureport.testng.model;

import java.util.List;

/** Payload for a single test result submitted to POST /api/test/multi. */
public class TestPayload {
    private String uid;
    private String name;
    private String build;
    private String status;
    private String startTime;
    private String endTime;
    private boolean isRerun;
    private TestFailure failure;
    private TestInfo info;
    private List<StepPayload> setup;
    private List<StepPayload> body;
    private List<StepPayload> teardown;

    public TestPayload() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBuild() { return build; }
    public void setBuild(String build) { this.build = build; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isRerun() { return isRerun; }
    public void setRerun(boolean rerun) { isRerun = rerun; }

    public TestFailure getFailure() { return failure; }
    public void setFailure(TestFailure failure) { this.failure = failure; }

    public TestInfo getInfo() { return info; }
    public void setInfo(TestInfo info) { this.info = info; }

    public List<StepPayload> getSetup() { return setup; }
    public void setSetup(List<StepPayload> setup) { this.setup = setup; }

    public List<StepPayload> getBody() { return body; }
    public void setBody(List<StepPayload> body) { this.body = body; }

    public List<StepPayload> getTeardown() { return teardown; }
    public void setTeardown(List<StepPayload> teardown) { this.teardown = teardown; }
}
