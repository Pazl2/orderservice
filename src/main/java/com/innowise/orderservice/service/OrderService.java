package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dto.*;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.specification.OrderSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserClient userClient;

    public OrderService(OrderRepository orderRepository,
                        ItemRepository itemRepository,
                        OrderMapper orderMapper,
                        UserClient userClient) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.orderMapper = orderMapper;
        this.userClient = userClient;
    }

    @Transactional
    public OrderWithUserResponse createOrder(OrderCreateRequest dto) {
        UserDto user = userClient.getUserByEmail(dto.getUserEmail());

        Order order = new Order();
        order.setUserId(user.getId());
        order.setStatus(dto.getStatus());
        order.setDeleted(false);

        BigDecimal totalPrice = buildOrderItems(order, dto.getItems());
        order.setTotalPrice(totalPrice);

        Order saved = orderRepository.save(order);
        return wrap(saved, user);
    }

    public OrderWithUserResponse getOrderById(Long id) {
        Order order = getOrderEntityById(id);
        UserDto user = userClient.getUserById(order.getUserId());
        return wrap(order, user);
    }

    public Page<OrderWithUserResponse> getOrders(LocalDateTime createdFrom,
                                                 LocalDateTime createdTo,
                                                 List<OrderStatus> statuses,
                                                 int page,
                                                 int size) {
        Specification<Order> spec = Specification
                .allOf(OrderSpecification.createdAfter(createdFrom),
                        OrderSpecification.createdBefore(createdTo),
                        OrderSpecification.hasStatuses(statuses));

        Pageable pageable = PageRequest.of(page, size);
        return orderRepository.findAll(spec, pageable)
                .map(order -> wrap(order, userClient.getUserById(order.getUserId())));
    }

    public List<OrderWithUserResponse> getOrdersByUserId(Long userId) {
        UserDto user = userClient.getUserById(userId);
        return orderRepository.findByUserId(userId).stream()
                .map(order -> wrap(order, user))
                .toList();
    }

    @Transactional
    public OrderWithUserResponse updateOrder(Long id, OrderUpdateRequest dto) {
        Order order = getOrderEntityById(id);
        order.setStatus(dto.getStatus());

        order.clearOrderItems();
        BigDecimal totalPrice = buildOrderItems(order, dto.getItems());
        order.setTotalPrice(totalPrice);

        Order saved = orderRepository.save(order);
        UserDto user = userClient.getUserById(saved.getUserId());
        return wrap(saved, user);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = getOrderEntityById(id);
        order.setDeleted(true);
        orderRepository.save(order);
    }

    private BigDecimal buildOrderItems(Order order, List<OrderItemRequest> itemRequests) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : itemRequests) {
            Item item = itemRepository.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No such item with " + itemRequest.getItemId() + " id"));

            OrderItem orderItem = new OrderItem();
            orderItem.setItem(item);
            orderItem.setQuantity(itemRequest.getQuantity());
            order.addOrderItem(orderItem);

            total = total.add(item.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }
        return total;
    }

    private Order getOrderEntityById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No such order with " + id + " id"));
    }

    private OrderWithUserResponse wrap(Order order, UserDto user) {
        OrderResponse orderResponse = orderMapper.toDto(order);
        return new OrderWithUserResponse(orderResponse, user);
    }
}
