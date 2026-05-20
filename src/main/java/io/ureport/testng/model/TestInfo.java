package io.ureport.testng.model;

import java.util.List;
import java.util.Map;

/** Optional metadata block attached to a test result. */
public class TestInfo {
    private String file;
    private String path;
    private Long duration;
    private List<String> tags;
    private List<String> components;
    private List<String> teams;
    private List<Map<String, Object>> quickInfo;

    public TestInfo() {}

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Long getDuration() { return duration; }
    public void setDuration(Long duration) { this.duration = duration; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) { this.components = components; }

    public List<String> getTeams() { return teams; }
    public void setTeams(List<String> teams) { this.teams = teams; }

    public List<Map<String, Object>> getQuickInfo() { return quickInfo; }
    public void setQuickInfo(List<Map<String, Object>> quickInfo) { this.quickInfo = quickInfo; }
}
