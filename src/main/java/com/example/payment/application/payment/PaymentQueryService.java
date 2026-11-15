package com.example.payment.application.payment;

import com.example.payment.application.payment.dto.CancelResult;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.domain.payment.Payment;
import com.example.payment.domain.payment.PaymentRepository;
import com.example.payment.global.error.exception.PaymentNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public PaymentResult find(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResult::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    // 5장: 이 결제에 온 취소를 받은 순서대로
    @Transactional(readOnly = true)
    public List<CancelResult> findCancels(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
        return payment.getCancels().stream()
                .map(cancel -> CancelResults.of(payment, cancel))
                .toList();
    }
}
