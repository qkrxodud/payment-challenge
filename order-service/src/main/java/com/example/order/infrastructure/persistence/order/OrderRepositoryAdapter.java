package com.example.order.infrastructure.persistence.order;

import com.example.order.domain.order.Order;
import com.example.order.domain.order.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository springDataOrderRepository;

    @Override
    public Order save(Order order) {
        return springDataOrderRepository.save(order);
    }

    @Override
    public Optional<Order> findByOrderId(String orderId) {
        return springDataOrderRepository.findByOrderId(orderId);
    }
}
