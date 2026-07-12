package com.innowise.orderservice.integration;

import com.innowise.orderservice.dto.OrderCreateRequest;
import com.innowise.orderservice.dto.OrderItemRequest;
import com.innowise.orderservice.dto.OrderUpdateRequest;
import com.innowise.orderservice.dto.OrderWithUserResponse;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.integration.config.BaseIntegrationTest;
import com.innowise.orderservice.repository.ItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class OrderControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM order_items");
        jdbcTemplate.execute("DELETE FROM orders");
        itemRepository.deleteAll();
    }

    private Item createItem(String name, String price) {
        Item item = new Item();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        return itemRepository.save(item);
    }

    private void stubUserByEmail(String email, Long id) {
        wireMockServer.stubFor(get(urlPathMatching("/users/email/.+"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(id, email))));
    }

    private void stubUserById(Long id, String email) {
        wireMockServer.stubFor(get(urlPathEqualTo("/users/" + id))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(id, email))));
    }

    private OrderCreateRequest sampleCreateRequest(String email, Long itemId, int qty) {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserEmail(email);
        request.setStatus(OrderStatus.CREATED);
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setItemId(itemId);
        itemReq.setQuantity(qty);
        request.setItems(List.of(itemReq));
        return request;
    }

    @Test
    void shouldCreateOrderAndReturnUserInfoFromUserService() {
        Item item = createItem("Book", "15.00");
        stubUserByEmail("ivan@example.com", 1L);

        ResponseEntity<OrderWithUserResponse> response = restTemplate.exchange(
                "/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 2)),
                OrderWithUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getOrder().getId()).isNotNull();
        assertThat(response.getBody().getOrder().getTotalPrice()).isEqualByComparingTo("30.00");
        assertThat(response.getBody().getOrder().getItems()).hasSize(1);
        assertThat(response.getBody().getUser()).isNotNull();
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("ivan@example.com");
    }

    @Test
    void shouldGetOrderByIdWithUserInfo() {
        Item item = createItem("Pen", "5.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        Long orderId = restTemplate.exchange(
                "/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class).getBody().getOrder().getId();

        ResponseEntity<OrderWithUserResponse> response = restTemplate.exchange(
                "/orders/" + orderId, HttpMethod.GET, null, OrderWithUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getOrder().getId()).isEqualTo(orderId);
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("ivan@example.com");
    }

    @Test
    void shouldReturnNotFoundForMissingOrder() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/99999", HttpMethod.GET, null, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldUpdateOrder() {
        Item item = createItem("Book", "10.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        Long orderId = restTemplate.exchange(
                "/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class).getBody().getOrder().getId();

        OrderUpdateRequest updateRequest = new OrderUpdateRequest();
        updateRequest.setStatus(OrderStatus.PAID);
        OrderItemRequest itemReq = new OrderItemRequest();
        itemReq.setItemId(item.getId());
        itemReq.setQuantity(4);
        updateRequest.setItems(List.of(itemReq));

        ResponseEntity<OrderWithUserResponse> response = restTemplate.exchange(
                "/orders/" + orderId, HttpMethod.PUT,
                new HttpEntity<>(updateRequest), OrderWithUserResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(response.getBody().getOrder().getTotalPrice()).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldSoftDeleteOrder() {
        Item item = createItem("Book", "10.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        Long orderId = restTemplate.exchange(
                "/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class).getBody().getOrder().getId();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/orders/" + orderId, HttpMethod.DELETE, null, Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                "/orders/" + orderId, HttpMethod.GET, null, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Long physicalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE id = ?", Long.class, orderId);
        Long deletedRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE id = ? AND deleted = true", Long.class, orderId);
        assertThat(physicalRows).isEqualTo(1L);
        assertThat(deletedRows).isEqualTo(1L);
    }

    @Test
    void shouldReturnServiceUnavailableWhenUserServiceDown() {
        Item item = createItem("Book", "10.00");
        wireMockServer.stubFor(get(urlPathMatching("/users/email/.+"))
                .willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldGetOrdersFilteredByUserId() throws Exception {
        Item item = createItem("Book", "10.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        restTemplate.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders?userId=1", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("content")).hasSize(1);
        assertThat(body.get("totalElements").asInt()).isEqualTo(1);
        assertThat(body.get("content").get(0).get("user").get("id").asLong()).isEqualTo(1L);
    }

    @Test
    void shouldReturnPagedOrdersFilteredByStatus() throws Exception {
        Item item = createItem("Book", "10.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        restTemplate.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class);
        restTemplate.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 2)),
                OrderWithUserResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders?statuses=CREATED&page=0&size=1", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("content")).hasSize(1);
        assertThat(body.get("totalElements").asInt()).isEqualTo(2);
        assertThat(body.get("totalPages").asInt()).isEqualTo(2);

        ResponseEntity<String> noneResponse = restTemplate.exchange(
                "/orders?statuses=DELIVERED", HttpMethod.GET, null, String.class);
        JsonNode noneBody = objectMapper.readTree(noneResponse.getBody());
        assertThat(noneBody.get("totalElements").asInt()).isZero();
    }

    @Test
    void shouldReturnEmptyWhenDateRangeExcludesOrders() throws Exception {
        Item item = createItem("Book", "10.00");
        stubUserByEmail("ivan@example.com", 1L);
        stubUserById(1L, "ivan@example.com");

        restTemplate.exchange("/orders", HttpMethod.POST,
                new HttpEntity<>(sampleCreateRequest("ivan@example.com", item.getId(), 1)),
                OrderWithUserResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders?createdFrom=2999-01-01T00:00:00", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.get("totalElements").asInt()).isZero();
    }
}
