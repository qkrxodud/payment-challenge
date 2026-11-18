package com.example.payment.infrastructure.persistence.product;

import com.example.payment.domain.product.Product;
import com.example.payment.domain.product.ProductRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final SpringDataProductRepository springDataProductRepository;

    @Override
    public Product save(Product product) {
        return springDataProductRepository.save(product);
    }

    @Override
    public List<Product> findAllById(Collection<Long> ids) {
        return springDataProductRepository.findAllById(ids);
    }
}
