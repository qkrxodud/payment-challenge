package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface SpringDataOrderRepository extends JpaRepository<Order, Long> {

    // 11장 결정 2: select … for update
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findForUpdateByOrderId(String orderId);
}
