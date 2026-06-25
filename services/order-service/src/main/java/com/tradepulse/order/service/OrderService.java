package com.tradepulse.order.service;

import com.tradepulse.common.dto.response.PageResponse;
import com.tradepulse.order.dto.request.PlaceOrderRequest;
import com.tradepulse.order.dto.response.OrderResponse;

import java.util.UUID;

public interface OrderService {

    OrderResponse placeOrder(UUID userId, PlaceOrderRequest request);

    OrderResponse cancelOrder(UUID orderId, UUID userId);

    OrderResponse getOrder(UUID orderId, UUID userId);

    PageResponse<OrderResponse> listOrders(UUID userId, int page, int size);
}
