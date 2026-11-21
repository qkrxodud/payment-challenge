package com.example.order.domain.order;

import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    // 11장 결정 2: 주문 줄을 잠그고 읽는다
    Optional<Order> findByOrderIdForUpdate(String orderId);
}
