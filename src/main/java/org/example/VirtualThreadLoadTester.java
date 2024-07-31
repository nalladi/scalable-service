package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class VirtualThreadLoadTester {

    private static final String PROCESS_URL = "http://localhost:8080/api/process";
    private static final String STRUCTURED_URL = "http://localhost:8080/api/structured";
    private static final int TOTAL_REQUESTS = 1_000_000;
    private static final int CONCURRENT_USERS = 100_000;
    private static final Duration TEST_DURATION = Duration.ofMinutes(5);

    private final AtomicInteger successfulRequests = new AtomicInteger(0);
    private final AtomicInteger failedRequests = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void runLoadTest() throws Exception {
        System.out.println("Starting load test...");
        long startTime = System.currentTimeMillis();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < TOTAL_REQUESTS; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        sendRequest(PROCESS_URL);
                        sendRequest(STRUCTURED_URL);
                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                    }
                }, executor);

                futures.add(future);

                if (futures.size() >= CONCURRENT_USERS || i == TOTAL_REQUESTS - 1) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    futures.clear();

                    if (System.currentTimeMillis() - startTime > TEST_DURATION.toMillis()) {
                        break;
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        printResults(endTime - startTime);
    }

    private void sendRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long endTime = System.currentTimeMillis();

        if (response.statusCode() == 200) {
            successfulRequests.incrementAndGet();
            totalResponseTime.addAndGet(endTime - startTime);
        } else {
            failedRequests.incrementAndGet();
        }
    }

    private void printResults(long totalTime) {
        int totalRequests = successfulRequests.get() + failedRequests.get();
        double avgResponseTime = totalResponseTime.get() / (double) successfulRequests.get();

        System.out.println("Load Test Results:");
        System.out.println("Total Requests: " + totalRequests);
        System.out.println("Successful Requests: " + successfulRequests.get());
        System.out.println("Failed Requests: " + failedRequests.get());
        System.out.println("Average Response Time: " + String.format("%.2f", avgResponseTime) + " ms");
        System.out.println("Total Test Time: " + String.format("%.2f", totalTime / 1000.0) + " seconds");
        System.out.println("Requests per Second: " + String.format("%.2f", totalRequests / (totalTime / 1000.0)));
    }

    public static void main(String[] args) throws Exception {
        new VirtualThreadLoadTester().runLoadTest();
    }
}