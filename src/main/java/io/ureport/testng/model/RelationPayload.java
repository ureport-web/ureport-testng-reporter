package io.ureport.testng.model;

import java.util.List;
import java.util.Map;

/** Payload for POST /api/test_relation. */
public class RelationPayload {
    private String uid;
    private String product;
    private String type;
    private String file;
    private String path;
    private List<String> tags;
    private List<String> components;
    private List<String> teams;
    private Map<String, Object> customs;

    public RelationPayload() {}

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getComponents() { return components; }
    public void setComponents(List<String> components) { this.components = components; }

    public List<String> getTeams() { return teams; }
    public void setTeams(List<String> teams) { this.teams = teams; }

    public Map<String, Object> getCustoms() { return customs; }
    public void setCustoms(Map<String, Object> customs) { this.customs = customs; }
}
