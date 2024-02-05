package br.com.matuto.http;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimpleHttpClientTest {

    private final SimpleHttpClient client = new SimpleHttpClient();
    private WireMockServer wireMockServer;

    @BeforeEach
    public void setup() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    public void teardown() {
        wireMockServer.stop();
    }

    @Test
    public void testRetrySuccessAfterFailure() throws Exception {
        // Configura o WireMock para simular uma falha seguida de sucesso
        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500).withBody("Error"))
                .willSetStateTo("Failed Once"));

        wireMockServer.stubFor(get(urlEqualTo("/test"))
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Failed Once")
                .willReturn(aResponse().withStatus(200).withBody("Success")));

        SimpleHttpClient httpClient = new SimpleHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + wireMockServer.port() + "/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.makeRequest(request);

        assertEquals(200, response.statusCode());
        assertEquals("Success", response.body());
    }


    @Test
    void makeRequest() throws Exception{
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://httpbin.org/get"))
                .build();
        HttpResponse response = client.makeRequest(request);
        assertEquals(200, response.statusCode());

    }

    @Test
    void makeAsyncRequest() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://httpbin.org/get"))
                .build();
        CompletableFuture<HttpResponse<String>> futureResponse = client.makeAsyncRequest(request);
        futureResponse.thenAccept(response -> assertEquals(200, response.statusCode()));

    }

    @Test
    public void testCircuitBreakerOpens() {
        int failThreshold = 5;
        // Configura respostas falhas
        stubFor(get(urlEqualTo("/test"))
                .willReturn(aResponse().withStatus(500)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:%d/test", wireMockServer.port())))
                .build();

        // Simula falhas até atingir o limite do circuit breaker
        for (int i = 0; i < failThreshold; i++) {
            try {
                client.makeRequest(request);
            } catch (Exception ignored) {
                // Ignora as exceções esperadas durante a simulação de falhas
            }
        }

        // Tenta uma chamada adicional para verificar se o circuit breaker está aberto
        assertThrows(CallNotPermittedException.class, () -> client.makeRequest(request),
                "Deveria lançar CallNotPermittedException indicando que o circuit breaker está aberto");
    }

    @Test
    public void testTimeoutSetting() throws Exception {
        client.withTimeout(Duration.ofSeconds(1)); // Configura um timeout muito curto

        WireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/timeout"))
                .willReturn(WireMock.aResponse().withFixedDelay(2000))); // Delay superior ao timeout

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format("http://localhost:%d/timeout", wireMockServer.port())))
                .GET()
                .build();

        assertThrows(IOException.class, () -> client.makeRequest(request),
                "Deveria lançar uma IOException devido ao timeout");
    }
}