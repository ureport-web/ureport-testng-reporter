package io.ureport.testng.model;

/** Payload for POST /api/build. */
public class BuildPayload {
    private String product;
    private String type;
    private long build;
    private String team;
    private String browser;
    private String device;
    private String platform;
    private String platformVersion;
    private String stage;
    private String version;
    private String startTime;

    public BuildPayload() {}

    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getBuild() { return build; }
    public void setBuild(long build) { this.build = build; }

    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }

    public String getBrowser() { return browser; }
    public void setBrowser(String browser) { this.browser = browser; }

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getPlatformVersion() { return platformVersion; }
    public void setPlatformVersion(String platformVersion) { this.platformVersion = platformVersion; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
}
