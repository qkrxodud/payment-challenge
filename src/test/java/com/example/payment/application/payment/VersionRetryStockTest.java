package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.RetryOnStockChangeConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.infrastructure.persistence.stock.comparison.VersionCheckStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import({VersionCheckStockRepository.class, RetryOnStockChangeConfirmService.class})
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema.sql,classpath:stock-version.sql")
@DisplayName("같은 상품에 몰린 주문의 재고 — 비교: 변경 번호 + 처음부터 다시 시도")
class VersionRetryStockTest extends StockContract {

    @Autowired
    RetryOnStockChangeConfirmService service;

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }
}
