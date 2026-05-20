package io.ureport.testng;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client for the UReport REST API.
 *
 * <p>Uses {@link java.net.http.HttpClient} (Java 11+) and Jackson for JSON serialization.
 * Every request carries an {@code Authorization: Bearer} header.
 */
public class UReportApiClient {

    private final String serverUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UReportApiClient(String serverUrl, String apiToken) {
        this.serverUrl = serverUrl.replaceAll("/$", "");
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /** POST /api/build — returns the new build {@code _id}. */
    public String createBuild(Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        HttpResponse<String> response = post("/api/build", body);
        assertSuccess(response, "create build");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
        return (String) result.get("_id");
    }

    /** POST /api/test/multi — submits a batch of test results. */
    public void submitTests(List<Map<String, Object>> tests) throws Exception {
        Map<String, Object> payload = Map.of("tests", tests);
        String body = objectMapper.writeValueAsString(payload);
        HttpResponse<String> response = post("/api/test/multi", body);
        assertSuccess(response, "submit tests");
    }

    /** POST /api/build/status/calculate/{buildId} — finalizes the build status. */
    public void finalizeBuild(String buildId) throws Exception {
        HttpResponse<String> response = post("/api/build/status/calculate/" + buildId, "{}");
        assertSuccess(response, "finalize build");
    }

    /** POST /api/test_relation — saves one test relation. */
    public void saveRelation(Map<String, Object> relation) throws Exception {
        String body = objectMapper.writeValueAsString(relation);
        HttpResponse<String> response = post("/api/test_relation", body);
        assertSuccess(response, "save relation");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + path))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void assertSuccess(HttpResponse<String> response, String operation) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                    "[ureport-testng-reporter] Failed to " + operation + ": " + status);
        }
    }
}
