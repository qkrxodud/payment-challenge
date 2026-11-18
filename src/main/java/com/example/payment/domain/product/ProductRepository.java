package com.example.payment.domain.product;

import java.util.Collection;
import java.util.List;

public interface ProductRepository {

    Product save(Product product);

    // 있는 상품만 나온다. 없는 번호는 빠진다
    List<Product> findAllById(Collection<Long> ids);
}
