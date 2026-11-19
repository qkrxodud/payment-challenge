package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataOrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(String orderId);
}
