package br.com.matuto.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

public class SimpleHttpClient implements HttpClient {

    private java.net.http.HttpClient httpClient;
    private Retry retry;
    private CircuitBreaker circuitBreaker;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

    public SimpleHttpClient() {
        this.httpClient = java.net.http.HttpClient.newHttpClient();
        initializeCircuitBreaker();
        initializeRetry();
    }

    private void initializeCircuitBreaker() {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50) // Percentual de falhas para abrir o circuit breaker
                .ringBufferSizeInClosedState(5) // Número de chamadas consideradas para calcular a taxa de falhas
                .waitDurationInOpenState(Duration.ofSeconds(10)) // Tempo de espera antes de tentar fechar
                .build();

        this.circuitBreaker = CircuitBreaker.of("defaultCircuitBreaker", circuitBreakerConfig);
    }

    private void initializeRetry() {
        RetryConfig retryConfig = RetryConfig.custom()
                .retryOnException(e -> e instanceof IOException)
                .retryOnResult(response -> {
                    if (response instanceof HttpResponse) {
                        return shouldRetry((HttpResponse<?>) response, null);
                    }
                    return false;
                })
                .build();

        this.retry = Retry.of("defaultRetry", retryConfig);
    }

    private boolean shouldRetry(HttpResponse<?> response, Throwable exception) {
        if (exception != null) {
            return exception instanceof IOException;
        }
        int status = response.statusCode();
        return status >= 500;
    }

    private <T> T executeWithResilience(Function<HttpRequest, T> httpRequestFunction, HttpRequest request){
        Function<HttpRequest, T> decorated = Retry.decorateFunction(retry,
                CircuitBreaker.decorateFunction(circuitBreaker, httpRequestFunction));
        return decorated.apply(request);
    }

    public HttpResponse makeRequest(HttpRequest request) throws IOException, InterruptedException, HttpTimeoutException{
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(request.uri())
                .timeout(Duration.ofMillis(3000)) // Configura o timeout aqui, se foi definido anteriormente.
                .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));
        request.headers().map().forEach((k, v) -> v.forEach(value -> requestBuilder.header(k, value)));

        HttpRequest requestWithTimeout = requestBuilder.build();

        try {
            return executeWithResilience(req -> {
                try {
                    return httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    try {
                        throw e;
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } catch (Exception e) {
                    // Para outras exceções, encapsula em RuntimeException
                    throw new RuntimeException("Erro ao executar request HTTP", e);
                }
            }, requestWithTimeout);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof InterruptedException) throw (InterruptedException) cause;
            throw e; // Relança RuntimeException para outras exceções encapsuladas.
        }
    }

    @Override
    public CompletableFuture<HttpResponse<String>> makeAsyncRequest(HttpRequest request) {

        Supplier<CompletionStage<HttpResponse<String>>> completionStageSupplier =
                () -> httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        Supplier<CompletionStage<HttpResponse<String>>> retrySupplier =
                () -> Retry.decorateCompletionStage(retry, scheduler, completionStageSupplier).get();

        return CircuitBreaker.decorateCompletionStage(circuitBreaker, retrySupplier).get().toCompletableFuture();
    }

    @Override
    public HttpClient withRetryPolicy(RetryConfig retryPolicy) {
        this.retry = Retry.of("customRetry", retryPolicy);
        return this;
    }

    @Override
    public HttpClient withCircuitBreaker(CircuitBreakerConfig circuitBreakerConfig) {
        this.circuitBreaker = CircuitBreaker.of("customCircuitBreaker", circuitBreakerConfig);
        return this;
    }

    public SimpleHttpClient withTimeout(Duration duration) {
        this.httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(duration)
                .build();
        return this;
    }

}
