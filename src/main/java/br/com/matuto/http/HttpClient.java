package br.com.matuto.http;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public interface HttpClient {

    HttpResponse makeRequest(HttpRequest request) throws Exception;
    public CompletableFuture<HttpResponse<String>> makeAsyncRequest(HttpRequest request);
    HttpClient withRetryPolicy(RetryConfig retryPolicy);
    HttpClient withCircuitBreaker(CircuitBreakerConfig circuitBreaker);
    SimpleHttpClient withTimeout(Duration duration);
}
