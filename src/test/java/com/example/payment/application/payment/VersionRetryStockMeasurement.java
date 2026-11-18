package com.example.payment.application.payment;

import com.example.payment.application.payment.comparison.RetryOnStockChangeConfirmService;
import com.example.payment.application.payment.dto.ConfirmPaymentCommand;
import com.example.payment.application.payment.dto.PaymentResult;
import com.example.payment.infrastructure.persistence.stock.comparison.VersionCheckStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import({VersionCheckStockRepository.class, RetryOnStockChangeConfirmService.class})
@TestPropertySource(properties = "spring.sql.init.schema-locations=classpath:schema.sql,classpath:stock-version.sql")
class VersionRetryStockMeasurement extends StockMeasurement {

    @Autowired
    RetryOnStockChangeConfirmService service;

    @Override
    String name() {
        return "변경 번호 + 다시 시도(최대 " + RetryOnStockChangeConfirmService.MAX_ATTEMPTS + "번)";
    }

    @Override
    PaymentResult confirm(ConfirmPaymentCommand command) {
        return service.confirm(command);
    }

    @Override
    String retries() {
        return " | 다시 시도 " + service.takeRetries() + "번";
    }
}
