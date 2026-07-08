package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.OrderCreateRequest;
import com.innowise.orderservice.dto.OrderItemRequest;
import com.innowise.orderservice.dto.OrderUpdateRequest;
import com.innowise.orderservice.dto.OrderWithUserResponse;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderItemMapperImpl;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.mapper.OrderMapperImpl;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserClient userClient;

    private final OrderItemMapper orderItemMapper = new OrderItemMapperImpl();
    private final OrderMapper orderMapper = new OrderMapperImpl(orderItemMapper);

    private OrderService orderService;

    @Test
    void applyPaymentStatus_success_setsOrderStatusToPaid() {
        Order order = new Order();
        order.setId(105L);
        order.setStatus(OrderStatus.CREATED);

        doReturn(Optional.of(order)).when(orderRepository).findById(105L);

        orderService.applyPaymentStatus(105L, "SUCCESS");

        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void applyPaymentStatus_failed_setsOrderStatusToCancelled() {
        Order order = new Order();
        order.setId(106L);
        order.setStatus(OrderStatus.CREATED);

        doReturn(Optional.of(order)).when(orderRepository).findById(106L);

        orderService.applyPaymentStatus(106L, "FAILED");

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void applyPaymentStatus_alreadyApplied_isIdempotent_doesNotSaveAgain() {
        Order order = new Order();
        order.setId(107L);
        order.setStatus(OrderStatus.PAID);

        doReturn(Optional.of(order)).when(orderRepository).findById(107L);

        orderService.applyPaymentStatus(107L, "SUCCESS");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void applyPaymentStatus_orderNotFound_throwsResourceNotFoundException() {
        doReturn(Optional.empty()).when(orderRepository).findById(999L);

        assertThrows(ResourceNotFoundException.class,
                () -> orderService.applyPaymentStatus(999L, "SUCCESS"));

        verify(orderRepository, never()).save(any());
    }


    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, itemRepository, orderMapper, orderItemMapper, userClient);
    }

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

    private Order orderWithUser(Long id, Long userId) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(OrderStatus.CREATED);
        return order;
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

        Order savedOrder = orderWithUser(100L, 1L);
        doReturn(savedOrder).when(orderRepository).save(any(Order.class));

        OrderWithUserResponse result = orderService.createOrder(request);

        assertNotNull(result);
        assertEquals(100L, result.getOrder().getId());
        assertEquals("ivan@example.com", result.getUser().getEmail());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order toSave = orderCaptor.getValue();
        assertEquals(0, new BigDecimal("35.50").compareTo(toSave.getTotalPrice()));
        assertEquals(1L, toSave.getUserId());
        assertEquals(OrderStatus.CREATED, toSave.getStatus());
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
        Order order = orderWithUser(5L, 1L);
        doReturn(Optional.of(order)).when(orderRepository).findById(5L);
        doReturn(sampleUser()).when(userClient).getUserById(1L);

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
    void getOrders_ShouldReturnPage_AndFetchEachUserOnce() {
        Order order1 = orderWithUser(7L, 1L);
        Order order2 = orderWithUser(8L, 1L);
        Page<Order> page = new PageImpl<>(List.of(order1, order2), PageRequest.of(0, 10), 2);

        doReturn(page).when(orderRepository).findAll(any(Specification.class), any(Pageable.class));
        doReturn(sampleUser()).when(userClient).getUserById(1L);

        Page<OrderWithUserResponse> result = orderService.getOrders(
                LocalDateTime.of(2024, Month.APRIL, 1, 0, 0), LocalDateTime.of(2024, Month.APRIL, 2, 0, 0),
                List.of(OrderStatus.CREATED), 1L, 0, 10);

        assertEquals(2, result.getTotalElements());
        assertEquals(7L, result.getContent().get(0).getOrder().getId());
        assertEquals(1L, result.getContent().get(0).getUser().getId());
        verify(userClient, times(1)).getUserById(1L);
        verify(orderRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void updateOrder_ShouldUpdateStatusAndItems_WhenExists() {
        Order existing = orderWithUser(3L, 1L);

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setStatus(OrderStatus.PAID);
        request.setItems(List.of(itemRequest(10L, 3)));

        doReturn(Optional.of(existing)).when(orderRepository).findById(3L);
        doReturn(Optional.of(sampleItem(10L, "Book", "10.00"))).when(itemRepository).findById(10L);
        doReturn(existing).when(orderRepository).save(existing);
        doReturn(sampleUser()).when(userClient).getUserById(1L);

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