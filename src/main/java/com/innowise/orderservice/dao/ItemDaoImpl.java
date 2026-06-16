package com.innowise.orderservice.dao;

import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.repository.ItemRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ItemDaoImpl implements ItemDao {

    private final ItemRepository itemRepository;

    public ItemDaoImpl(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Override
    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }
}
