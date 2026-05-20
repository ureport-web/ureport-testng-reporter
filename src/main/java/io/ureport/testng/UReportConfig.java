package io.ureport.testng;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads UReport configuration from system properties and/or {@code ureport.properties}.
 *
 * <p>Priority: system property ({@code -Dureport.xxx}) > {@code ureport.properties} in working
 * directory > built-in defaults.
 */
public class UReportConfig {

    public final String serverUrl;
    public final String apiToken;
    public final String product;
    public final String type;
    public final long buildNumber;
    public final String team;
    public final String browser;
    public final String device;
    public final String platform;
    public final String platformVersion;
    public final String stage;
    public final String version;
    public final int batchSize;
    public final boolean saveRelations;
    public final String outputFile;
    public final boolean includeSteps;

    public UReportConfig() {
        Properties props = loadProperties();

        serverUrl = require(props, "ureport.serverUrl");
        apiToken = require(props, "ureport.apiToken");
        product = require(props, "ureport.product");
        type = require(props, "ureport.type");

        String bn = get(props, "ureport.buildNumber");
        buildNumber = (bn != null) ? Long.parseLong(bn) : System.currentTimeMillis();

        team = get(props, "ureport.team");
        browser = get(props, "ureport.browser");
        device = get(props, "ureport.device");
        platform = get(props, "ureport.platform");
        platformVersion = get(props, "ureport.platform_version");
        stage = get(props, "ureport.stage");
        version = get(props, "ureport.version");

        String bs = get(props, "ureport.batchSize");
        batchSize = (bs != null) ? Integer.parseInt(bs) : 50;

        String sr = get(props, "ureport.saveRelations");
        saveRelations = (sr == null) || Boolean.parseBoolean(sr);

        outputFile = get(props, "ureport.outputFile");

        String is = get(props, "ureport.includeSteps");
        includeSteps = (is == null) || Boolean.parseBoolean(is);
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        File file = new File("ureport.properties");
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("[ureport-testng-reporter] Could not load ureport.properties: " + e.getMessage());
            }
        }
        return props;
    }

    private String get(Properties props, String key) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isEmpty()) return sys;
        String prop = props.getProperty(key);
        return (prop != null && !prop.isEmpty()) ? prop : null;
    }

    private String require(Properties props, String key) {
        String val = get(props, key);
        if (val == null) {
            throw new IllegalStateException("[ureport-testng-reporter] Missing required config: " + key);
        }
        return val;
    }
}
