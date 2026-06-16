package com.innowise.orderservice.dao;

import com.innowise.orderservice.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public interface OrderDao {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Page<Order> findAll(Specification<Order> specification, Pageable pageable);
}
