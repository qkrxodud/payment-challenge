package com.example.payment.application.payment.dto;

// 8장: 준비 요청에 담긴 상품 한 줄
public record OrderLine(Long productId, Integer quantity) {
}
