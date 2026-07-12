package com.innowise.orderservice.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class OrderItemResponse implements Serializable {
    private Long id;
    private Long itemId;
    private String itemName;
    private BigDecimal itemPrice;
    private Integer quantity;
}
