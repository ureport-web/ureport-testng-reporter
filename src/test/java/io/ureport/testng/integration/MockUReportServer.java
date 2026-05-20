package io.ureport.testng.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight HTTP server backed by {@code com.sun.net.httpserver} (JDK built-in).
 *
 * <p>Handles the four UReport endpoints and captures all request bodies for assertion in tests.
 */
public class MockUReportServer {

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String buildIdToReturn = "mock-build-id";
    private final List<Map<String, Object>> buildRequests = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> testMultiRequests = new CopyOnWriteArrayList<>();
    private final List<String> finalizeRequests = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> relationRequests = new CopyOnWriteArrayList<>();

    public MockUReportServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);

        server.createContext("/api/build", this::handleBuild);
        server.createContext("/api/test/multi", this::handleTestMulti);
        server.createContext("/api/test_relation", this::handleRelation);
        // /api/build/status/calculate/* — matched via prefix check in generic handler
        server.createContext("/api/build/status", this::handleFinalize);

        server.start();
    }

    private void handleBuild(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())
                && exchange.getRequestURI().getPath().equals("/api/build")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = mapper.readValue(readBody(exchange), Map.class);
            buildRequests.add(body);
            respond(exchange, 200, "{\"_id\":\"" + buildIdToReturn + "\"}");
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleTestMulti(HttpExchange exchange) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(readBody(exchange), Map.class);
        testMultiRequests.add(body);
        respond(exchange, 200, "{}");
    }

    private void handleFinalize(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.contains("/calculate/")) {
            finalizeRequests.add(path);
            respond(exchange, 200, "{}");
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
    }

    private void handleRelation(HttpExchange exchange) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = mapper.readValue(readBody(exchange), Map.class);
        relationRequests.add(body);
        respond(exchange, 200, "{}");
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void setBuildIdToReturn(String id) {
        this.buildIdToReturn = id;
    }

    public List<Map<String, Object>> getBuildRequests() {
        return new ArrayList<>(buildRequests);
    }

    /** Returns all bodies from POST /api/test/multi calls. */
    public List<Map<String, Object>> getTestMultiRequests() {
        return new ArrayList<>(testMultiRequests);
    }

    /** Returns all paths hit on /api/build/status/calculate/*. */
    public List<String> getFinalizeRequests() {
        return new ArrayList<>(finalizeRequests);
    }

    public List<Map<String, Object>> getRelationRequests() {
        return new ArrayList<>(relationRequests);
    }

    /** Flattens all tests from all /api/test/multi call payloads into one list. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllSubmittedTests() {
        List<Map<String, Object>> all = new ArrayList<>();
        for (Map<String, Object> req : testMultiRequests) {
            Object tests = req.get("tests");
            if (tests instanceof List) {
                all.addAll((List<Map<String, Object>>) tests);
            }
        }
        return all;
    }
}
