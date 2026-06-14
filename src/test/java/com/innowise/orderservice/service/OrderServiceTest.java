package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.*;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private OrderService orderService;

    private UserDto sampleUser() {
        UserDto user = new UserDto();
        user.setId(1L);
        user.setEmail("ivan@example.com");
        user.setName("Ivan");
        return user;
    }

    private Item sampleItem(Long id, String name, String price) {
        Item item = new Item();
        item.setId(id);
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private OrderItemRequest itemRequest(Long itemId, int quantity) {
        OrderItemRequest req = new OrderItemRequest();
        req.setItemId(itemId);
        req.setQuantity(quantity);
        return req;
    }

    @Test
    void createOrder_ShouldResolveUserByEmail_AndComputeTotalPrice() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserEmail("ivan@example.com");
        request.setStatus(OrderStatus.CREATED);
        request.setItems(List.of(itemRequest(10L, 2), itemRequest(20L, 1)));

        UserDto user = sampleUser();
        doReturn(user).when(userClient).getUserByEmail("ivan@example.com");
        doReturn(Optional.of(sampleItem(10L, "Book", "15.00"))).when(itemRepository).findById(10L);
        doReturn(Optional.of(sampleItem(20L, "Pen", "5.50"))).when(itemRepository).findById(20L);

        Order savedOrder = new Order();
        savedOrder.setId(100L);
        savedOrder.setUserId(1L);
        savedOrder.setStatus(OrderStatus.CREATED);
        doReturn(savedOrder).when(orderRepository).save(any(Order.class));

        OrderResponse mappedResponse = new OrderResponse();
        mappedResponse.setId(100L);
        doReturn(mappedResponse).when(orderMapper).toDto(savedOrder);

        OrderWithUserResponse result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(100L, result.getOrder().getId());
        assertEquals("ivan@example.com", result.getUser().getEmail());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order toSave = orderCaptor.getValue();
        // 15.00 * 2 + 5.50 * 1 = 35.50
        assertEquals(0, new BigDecimal("35.50").compareTo(toSave.getTotalPrice()));
        assertEquals(1L, toSave.getUserId());
        assertEquals(2, toSave.getOrderItems().size());

        verify(userClient, times(1)).getUserByEmail("ivan@example.com");
    }

    @Test
    void createOrder_ShouldThrow_WhenItemNotFound() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setUserEmail("ivan@example.com");
        request.setStatus(OrderStatus.CREATED);
        request.setItems(List.of(itemRequest(99L, 1)));

        doReturn(sampleUser()).when(userClient).getUserByEmail("ivan@example.com");
        doReturn(Optional.empty()).when(itemRepository).findById(99L);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.createOrder(request));
        assertTrue(ex.getMessage().contains("99"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void getOrderById_ShouldReturnOrderWithUser_WhenExists() {
        Order order = new Order();
        order.setId(5L);
        order.setUserId(1L);
        doReturn(Optional.of(order)).when(orderRepository).findById(5L);
        doReturn(sampleUser()).when(userClient).getUserById(1L);

        OrderResponse response = new OrderResponse();
        response.setId(5L);
        doReturn(response).when(orderMapper).toDto(order);

        OrderWithUserResponse result = orderService.getOrderById(5L);

        assertNotNull(result);
        assertEquals(5L, result.getOrder().getId());
        assertEquals(1L, result.getUser().getId());
        verify(userClient, times(1)).getUserById(1L);
    }

    @Test
    void getOrderById_ShouldThrow_WhenOrderNotFound() {
        doReturn(Optional.empty()).when(orderRepository).findById(404L);

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> orderService.getOrderById(404L));
        assertTrue(ex.getMessage().contains("404"));
        verify(userClient, never()).getUserById(anyLong());
    }

    @Test
    void getOrders_ShouldReturnPageOfOrdersWithUser() {
        Order order = new Order();
        order.setId(7L);
        order.setUserId(1L);
        Page<Order> page = new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1);

        doReturn(page).when(orderRepository).findAll(any(Specification.class), any(Pageable.class));
        doReturn(sampleUser()).when(userClient).getUserById(1L);
        OrderResponse response = new OrderResponse();
        response.setId(7L);
        doReturn(response).when(orderMapper).toDto(order);

        Page<OrderWithUserResponse> result = orderService.getOrders(
                LocalDateTime.now().minusDays(1), LocalDateTime.now(),
                List.of(OrderStatus.CREATED), 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(7L, result.getContent().get(0).getOrder().getId());
        assertEquals(1L, result.getContent().get(0).getUser().getId());
        verify(orderRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getOrdersByUserId_ShouldFetchUserOnce_AndReturnOrders() {
        Order order1 = new Order();
        order1.setId(1L);
        order1.setUserId(1L);
        Order order2 = new Order();
        order2.setId(2L);
        order2.setUserId(1L);

        doReturn(sampleUser()).when(userClient).getUserById(1L);
        doReturn(List.of(order1, order2)).when(orderRepository).findByUserId(1L);
        doReturn(new OrderResponse()).when(orderMapper).toDto(any(Order.class));

        List<OrderWithUserResponse> result = orderService.getOrdersByUserId(1L);

        assertEquals(2, result.size());
        verify(userClient, times(1)).getUserById(1L);
        verify(orderRepository, times(1)).findByUserId(1L);
    }

    @Test
    void updateOrder_ShouldUpdateStatusAndItems_WhenExists() {
        Order existing = new Order();
        existing.setId(3L);
        existing.setUserId(1L);
        existing.setStatus(OrderStatus.CREATED);

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setStatus(OrderStatus.PAID);
        request.setItems(List.of(itemRequest(10L, 3)));

        doReturn(Optional.of(existing)).when(orderRepository).findById(3L);
        doReturn(Optional.of(sampleItem(10L, "Book", "10.00"))).when(itemRepository).findById(10L);
        doReturn(existing).when(orderRepository).save(existing);
        doReturn(sampleUser()).when(userClient).getUserById(1L);
        doReturn(new OrderResponse()).when(orderMapper).toDto(existing);

        OrderWithUserResponse result = orderService.updateOrder(3L, request);

        assertNotNull(result);
        assertEquals(OrderStatus.PAID, existing.getStatus());
        assertEquals(0, new BigDecimal("30.00").compareTo(existing.getTotalPrice()));
        assertEquals(1, existing.getOrderItems().size());
        verify(orderRepository, times(1)).save(existing);
    }

    @Test
    void updateOrder_ShouldThrow_WhenOrderNotFound() {
        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setStatus(OrderStatus.PAID);
        request.setItems(List.of(itemRequest(10L, 1)));

        doReturn(Optional.empty()).when(orderRepository).findById(404L);

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.updateOrder(404L, request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void deleteOrder_ShouldSoftDelete_WhenExists() {
        Order order = new Order();
        order.setId(8L);
        order.setDeleted(false);
        doReturn(Optional.of(order)).when(orderRepository).findById(8L);
        doReturn(order).when(orderRepository).save(order);

        orderService.deleteOrder(8L);

        assertTrue(order.isDeleted());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void deleteOrder_ShouldThrow_WhenOrderNotFound() {
        doReturn(Optional.empty()).when(orderRepository).findById(404L);

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.deleteOrder(404L));
        verify(orderRepository, never()).save(any());
    }
}
