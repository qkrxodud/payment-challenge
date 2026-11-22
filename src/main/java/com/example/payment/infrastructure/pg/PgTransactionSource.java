package com.example.payment.infrastructure.pg;

import com.example.payment.domain.error.exception.PaymentGatewayFailedException;
import com.example.payment.domain.error.exception.PaymentGatewayNoResponseException;
import com.example.payment.domain.payment.GatewayStatus;
import com.example.payment.domain.reconciliation.GatewayTransaction;
import com.example.payment.domain.reconciliation.GatewayTransactionSource;
import com.example.payment.global.config.DailyReconciliationProperties;
import com.example.payment.infrastructure.pg.dto.PgTransaction;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 12장 결정 1: 카드사 거래 조회(GET /v1/transactions). 토스페이먼츠 문서: startDate·endDate(yyyy-MM-dd'T'HH:mm:ss)로 처리한 때를 기준으로 고르고,
 * startingAfter(거래 키) 뒤부터 limit(최대 5000)건씩 쪽으로 나눠 받는다. 요청은 60초까지 걸릴 수 있어 시간 제한을 60초 이상으로 두라고 한다
 * (읽기 시간 제한은 payment.pg.transactions-read-timeout). 받은 쪽이 limit 보다 적으면 마지막 쪽으로 본다.
 * 날짜에는 시간대가 없어 대사 시간대(한국)의 그날 0시부터 23:59:59 까지로 묻는다 — 끝 초를 넣는지는 문서 예(23:59:59)에서 끌어낸 가정이다.
 */
@Component
@RequiredArgsConstructor
public class PgTransactionSource implements GatewayTransactionSource {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RestClient pgTransactionRestClient;
    private final DailyReconciliationProperties dailyReconciliationProperties;

    @Override
    public List<GatewayTransaction> findTransactions(LocalDate day) {
        List<GatewayTransaction> transactions = new ArrayList<>();
        String startingAfter = null;
        List<PgTransaction> page;
        do {
            page = page(day, startingAfter);
            page.stream().map(PgTransactionSource::toTransaction).forEach(transactions::add);
            startingAfter = lastKeyOf(page, startingAfter);
        } while (page.size() >= dailyReconciliationProperties.getPageSize());
        return transactions;
    }

    private List<PgTransaction> page(LocalDate day, String startingAfter) {
        try {
            return pgTransactionRestClient.get()
                    .uri(builder -> builder.path("/v1/transactions")
                            .queryParam("startDate", day.atStartOfDay().format(DATE_TIME))
                            .queryParam("endDate", day.atTime(23, 59, 59).format(DATE_TIME))
                            .queryParamIfPresent("startingAfter", Optional.ofNullable(startingAfter))
                            .queryParam("limit", dailyReconciliationProperties.getPageSize())
                            .build())
                    .exchange((request, response) -> toPage(day, response));
        } catch (ResourceAccessException noResponse) {
            throw new PaymentGatewayNoResponseException("transactions of " + day);
        }
    }

    private static List<PgTransaction> toPage(LocalDate day, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        if (response.getStatusCode().is2xxSuccessful()) {
            PgTransaction[] page = response.bodyTo(PgTransaction[].class);
            return Arrays.asList(Objects.requireNonNullElse(page, new PgTransaction[0]));
        }
        throw new PaymentGatewayFailedException("transactions of " + day, response.getStatusCode().value());
    }

    private static String lastKeyOf(List<PgTransaction> page, String previous) {
        if (page.isEmpty()) {
            return previous;
        }
        return page.get(page.size() - 1).transactionKey();
    }

    private static GatewayTransaction toTransaction(PgTransaction transaction) {
        return GatewayTransaction.of(transaction.transactionKey(), transaction.paymentKey(), transaction.orderId(),
                GatewayStatus.of(transaction.status()), OffsetDateTime.parse(transaction.transactionAt()).toInstant(),
                transaction.amount());
    }
}
