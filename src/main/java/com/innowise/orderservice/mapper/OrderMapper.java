package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.OrderResponse;
import com.innowise.orderservice.entity.Order;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {

    @Mapping(target = "items", source = "orderItems")
    OrderResponse toDto(Order entity);
}
