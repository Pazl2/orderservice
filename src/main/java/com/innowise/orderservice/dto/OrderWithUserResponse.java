package com.innowise.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Wrapper returned by all endpoints except delete: contains the order data
 * together with the user info fetched from the User Service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderWithUserResponse implements Serializable {
    private OrderResponse order;
    private UserDto user;
}
