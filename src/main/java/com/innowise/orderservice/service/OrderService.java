package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserClient;
import com.innowise.orderservice.dao.ItemDao;
import com.innowise.orderservice.dao.OrderDao;
import com.innowise.orderservice.dto.OrderCreateRequest;
import com.innowise.orderservice.dto.OrderItemRequest;
import com.innowise.orderservice.dto.OrderResponse;
import com.innowise.orderservice.dto.OrderUpdateRequest;
import com.innowise.orderservice.dto.OrderWithUserResponse;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.entity.Order;
import com.innowise.orderservice.entity.OrderItem;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.exception.ResourceNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.specification.OrderSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final OrderDao orderDao;
    private final ItemDao itemDao;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserClient userClient;

    public OrderService(OrderDao orderDao,
                        ItemDao itemDao,
                        OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        UserClient userClient) {
        this.orderDao = orderDao;
        this.itemDao = itemDao;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.userClient = userClient;
    }

    public OrderWithUserResponse createOrder(OrderCreateRequest dto) {
        UserDto user = userClient.getUserByEmail(dto.getUserEmail());

        Order order = orderMapper.toEntity(dto);
        order.setUserId(user.getId());
        order.setDeleted(false);

        BigDecimal totalPrice = buildOrderItems(order, dto.getItems());
        order.setTotalPrice(totalPrice);

        Order saved = orderDao.save(order);
        return wrap(saved, user);
    }

    @Transactional(readOnly = true)
    public OrderWithUserResponse getOrderById(Long id) {
        Order order = getOrderEntityById(id);
        UserDto user = userClient.getUserById(order.getUserId());
        return wrap(order, user);
    }

    @Transactional(readOnly = true)
    public Page<OrderWithUserResponse> getOrders(LocalDateTime createdFrom,
                                                 LocalDateTime createdTo,
                                                 List<OrderStatus> statuses,
                                                 Long userId,
                                                 int page,
                                                 int size) {
        Specification<Order> spec = Specification
                .allOf(OrderSpecification.createdAfter(createdFrom),
                        OrderSpecification.createdBefore(createdTo),
                        OrderSpecification.hasStatuses(statuses),
                        OrderSpecification.hasUserId(userId));

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderDao.findAll(spec, pageable);

        Map<Long, UserDto> usersById = resolveUsers(orders.getContent());
        return orders.map(order -> wrap(order, usersById.get(order.getUserId())));
    }

    @Transactional
    public OrderWithUserResponse updateOrder(Long id, OrderUpdateRequest dto) {
        Order order = getOrderEntityById(id);
        orderMapper.updateEntityFromDto(dto, order);

        order.clearOrderItems();
        BigDecimal totalPrice = buildOrderItems(order, dto.getItems());
        order.setTotalPrice(totalPrice);

        Order saved = orderDao.save(order);
        UserDto user = userClient.getUserById(saved.getUserId());
        return wrap(saved, user);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = getOrderEntityById(id);
        order.setDeleted(true);
        orderDao.save(order);
    }

    @Transactional
    public void applyPaymentStatus(Long orderId, String paymentStatus) {
        Order order = getOrderEntityById(orderId);

        OrderStatus newStatus = "SUCCESS".equals(paymentStatus)
                ? OrderStatus.PAID
                : OrderStatus.CANCELLED;

        if (order.getStatus() == newStatus) {
            return;
        }

        order.setStatus(newStatus);
        orderDao.save(order);
    }

    private Map<Long, UserDto> resolveUsers(List<Order> orders) {
        Map<Long, UserDto> usersById = new HashMap<>();
        for (Order order : orders) {
            usersById.computeIfAbsent(order.getUserId(), userClient::getUserById);
        }
        return usersById;
    }

    private BigDecimal buildOrderItems(Order order, List<OrderItemRequest> itemRequests) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemRequest : itemRequests) {
            Item item = itemDao.findById(itemRequest.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "No such item with " + itemRequest.getItemId() + " id"));

            OrderItem orderItem = orderItemMapper.toEntity(itemRequest);
            orderItem.setItem(item);
            order.addOrderItem(orderItem);

            total = total.add(item.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        }
        return total;
    }

    private Order getOrderEntityById(Long id) {
        return orderDao.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No such order with " + id + " id"));
    }

    private OrderWithUserResponse wrap(Order order, UserDto user) {
        OrderResponse orderResponse = orderMapper.toDto(order);
        return new OrderWithUserResponse(orderResponse, user);
    }
}