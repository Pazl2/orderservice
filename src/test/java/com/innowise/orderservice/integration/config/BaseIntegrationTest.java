package com.innowise.orderservice.integration.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.innowise.orderservice.OrderServiceApplication;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.annotation.PostConstruct;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OrderServiceApplication.class
)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
public abstract class BaseIntegrationTest {

    protected static WireMockServer wireMockServer;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeAll
    static void startWireMock() {
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(options().dynamicPort());
            wireMockServer.start();
        }
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("user.service.url", () -> {
            if (wireMockServer == null) {
                wireMockServer = new WireMockServer(options().dynamicPort());
                wireMockServer.start();
            }
            return "http://localhost:" + wireMockServer.port();
        });
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("userService").transitionToClosedState();
    }

    @PostConstruct
    void setupRestTemplate() {
        restTemplate.getRestTemplate().getInterceptors().add(
                (request, body, execution) -> {
                    request.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    request.getHeaders().set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    return execution.execute(request, body);
                }
        );
    }

    protected String userJson(Long id, String email) {
        return """
                {
                  "id": %d,
                  "name": "Ivan",
                  "surname": "Ivanov",
                  "birthDate": "1990-01-01",
                  "email": "%s",
                  "active": true
                }
                """.formatted(id, email);
    }
}