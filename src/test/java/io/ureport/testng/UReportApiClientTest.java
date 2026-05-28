package io.ureport.testng;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UReportApiClientTest {

    private HttpClient mockHttpClient;
    @SuppressWarnings("rawtypes")
    private HttpResponse mockResponse;
    private UReportApiClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        mockHttpClient = mock(HttpClient.class);
        mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{}");

        // Trailing slash: constructor strips it, then we inject mock HttpClient
        client = new UReportApiClient("http://ureport.test/", "tok-123");
        Field f = UReportApiClient.class.getDeclaredField("httpClient");
        f.setAccessible(true);
        f.set(client, mockHttpClient);

        when(mockHttpClient.send(any(HttpRequest.class), any())).thenReturn(mockResponse);
    }

    // ── constructor ───────────────────────────────────────────────────────────

    @Test
    void constructor_stripsTrailingSlash() throws Exception {
        when(mockResponse.body()).thenReturn("{\"_id\":\"b1\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.createBuild(Map.of("product", "P"));

        verify(mockHttpClient).send(captor.capture(), any());
        String uri = captor.getValue().uri().toString();
        assertFalse(uri.contains("//api"), "URI should not contain double-slash after stripping: " + uri);
        assertTrue(uri.endsWith("/api/build"), "URI should end with /api/build: " + uri);
    }

    // ── createBuild ───────────────────────────────────────────────────────────

    @Test
    void createBuild_postsToApiBuilд_andExtractsId() throws Exception {
        when(mockResponse.body()).thenReturn("{\"_id\":\"build-abc\",\"status\":\"created\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        String id = client.createBuild(Map.of("product", "MyApp", "type", "E2E"));

        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals("build-abc", id);
        HttpRequest req = captor.getValue();
        assertEquals("POST", req.method());
        assertTrue(req.uri().toString().endsWith("/api/build"));
    }

    @Test
    void createBuild_setsAuthorizationHeader() throws Exception {
        when(mockResponse.body()).thenReturn("{\"_id\":\"x\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.createBuild(Map.of());

        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals("Bearer tok-123",
                captor.getValue().headers().firstValue("Authorization").orElse(""));
    }

    @Test
    void createBuild_setsContentTypeHeader() throws Exception {
        when(mockResponse.body()).thenReturn("{\"_id\":\"x\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.createBuild(Map.of());

        verify(mockHttpClient).send(captor.capture(), any());
        assertEquals("application/json",
                captor.getValue().headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void createBuild_sendsPayloadAsJson() throws Exception {
        when(mockResponse.body()).thenReturn("{\"_id\":\"x\"}");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.createBuild(Map.of("product", "MyApp", "type", "E2E"));

        verify(mockHttpClient).send(captor.capture(), any());
        String body = readRequestBody(captor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(body, Map.class);
        assertEquals("MyApp", parsed.get("product"));
        assertEquals("E2E", parsed.get("type"));
    }

    // ── submitTests ───────────────────────────────────────────────────────────

    @Test
    void submitTests_postsToApiTestMulti() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.submitTests(List.of(Map.of("uid", "TC-001")));

        verify(mockHttpClient).send(captor.capture(), any());
        assertTrue(captor.getValue().uri().toString().endsWith("/api/test/multi"));
        assertEquals("POST", captor.getValue().method());
    }

    @Test
    void submitTests_wrapsListInTestsEnvelope() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.submitTests(List.of(Map.of("uid", "TC-001"), Map.of("uid", "TC-002")));

        verify(mockHttpClient).send(captor.capture(), any());
        String body = readRequestBody(captor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(body, Map.class);
        assertTrue(parsed.containsKey("tests"), "Body must have 'tests' key");
        @SuppressWarnings("unchecked")
        List<Object> tests = (List<Object>) parsed.get("tests");
        assertEquals(2, tests.size());
    }

    // ── finalizeBuild ─────────────────────────────────────────────────────────

    @Test
    void finalizeBuild_postsToCalculateUrl() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.finalizeBuild("my-build-id");

        verify(mockHttpClient).send(captor.capture(), any());
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.endsWith("/api/build/status/calculate/my-build-id"), "URI: " + uri);
        assertEquals("POST", captor.getValue().method());
    }

    @Test
    void finalizeBuild_sendsEmptyJsonBody() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.finalizeBuild("b1");

        verify(mockHttpClient).send(captor.capture(), any());
        String body = readRequestBody(captor.getValue());
        assertEquals("{}", body);
    }

    // ── saveRelation ──────────────────────────────────────────────────────────

    @Test
    void saveRelation_postsToTestRelation() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.saveRelation(Map.of("uid", "TC-001", "product", "MyApp"));

        verify(mockHttpClient).send(captor.capture(), any());
        assertTrue(captor.getValue().uri().toString().endsWith("/api/test_relation"));
        assertEquals("POST", captor.getValue().method());
    }

    @Test
    void saveRelation_sendsPayload() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        client.saveRelation(Map.of("uid", "TC-001"));

        verify(mockHttpClient).send(captor.capture(), any());
        String body = readRequestBody(captor.getValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(body, Map.class);
        assertEquals("TC-001", parsed.get("uid"));
    }

    // ── assertSuccess ─────────────────────────────────────────────────────────

    @Test
    void assertSuccess_throwsOnClientError() throws Exception {
        when(mockResponse.statusCode()).thenReturn(404);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.createBuild(Map.of()));
        assertTrue(ex.getMessage().contains("404") || ex.getMessage().contains("create build"),
                "Exception message: " + ex.getMessage());
    }

    @Test
    void assertSuccess_throwsOnServerError() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);

        assertThrows(RuntimeException.class, () -> client.submitTests(List.of()));
    }

    @Test
    void assertSuccess_doesNotThrowOn2xx() throws Exception {
        when(mockResponse.statusCode()).thenReturn(201);
        when(mockResponse.body()).thenReturn("{\"_id\":\"x\"}");

        assertDoesNotThrow(() -> client.createBuild(Map.of()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static String readRequestBody(HttpRequest request) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);
        request.bodyPublisher().ifPresent(pub -> pub.subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer b) {
                byte[] arr = new byte[b.remaining()];
                b.get(arr);
                out.write(arr, 0, arr.length);
            }
            public void onError(Throwable t) { latch.countDown(); }
            public void onComplete() { latch.countDown(); }
        }));
        latch.await(2, TimeUnit.SECONDS);
        return out.toString(StandardCharsets.UTF_8);
    }
}
