package com.innowise.orderservice.client;

import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UserClient {

    private static final Logger log = LoggerFactory.getLogger(UserClient.class);
    private static final String CIRCUIT_BREAKER = "userService";

    private final RestClient userServiceRestClient;

    public UserClient(RestClient userServiceRestClient) {
        this.userServiceRestClient = userServiceRestClient;
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getUserByEmailFallback")
    public UserDto getUserByEmail(String email) {
        return userServiceRestClient.get()
                .uri("/users/email/{email}", email)
                .retrieve()
                .body(UserDto.class);
    }

    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getUserByIdFallback")
    public UserDto getUserById(Long userId) {
        return userServiceRestClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(UserDto.class);
    }

    @SuppressWarnings("unused")
    private UserDto getUserByEmailFallback(String email, Throwable ex) {
        return handleFallback("email " + email, ex);
    }

    @SuppressWarnings("unused")
    private UserDto getUserByIdFallback(Long userId, Throwable ex) {
        return handleFallback("id " + userId, ex);
    }

    private UserDto handleFallback(String identifier, Throwable ex) {
        if (ex instanceof RestClientResponseException rcre
                && rcre.getStatusCode().value() == 404) {
            throw new ResourceNotFoundException("User not found by " + identifier);
        }
        log.warn("User service call failed for {}: {}", identifier, ex.getMessage());
        throw new UserServiceUnavailableException(
                "User service is currently unavailable", ex);
    }
}
