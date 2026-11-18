package com.example.payment.infrastructure.persistence.product;

import com.example.payment.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProductRepository extends JpaRepository<Product, Long> {
}
