package com.innowise.orderservice.dao;

import com.innowise.orderservice.entity.Item;

import java.util.Optional;

public interface ItemDao {

    Optional<Item> findById(Long id);
}
