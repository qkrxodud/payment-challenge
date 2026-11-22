package com.example.mockpg;

import com.example.mockpg.dto.TransactionResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 12장: 거래 조회(GET /v1/transactions). 토스페이먼츠 문서에서 확인한 것만 흉내 낸다:
 * - startDate·endDate(필수, yyyy-MM-dd'T'HH:mm:ss)는 거래가 처리된 때(transactionAt)를 기준으로 본다
 * - startingAfter(거래 키)를 주면 그 거래 뒤부터, limit(기본 100, 최대 5000)건까지 돌려준다
 * 문서에서 확인하지 못해 가정한 것: 날짜에 시간대가 없어 한국 시각으로 읽는다, endDate 는 그 초까지 넣는다(문서 예 23:59:59),
 * 5000 을 넘는 limit 은 5000 으로 줄인다(문서는 최댓값만 적는다), 순서는 처리한 때 순이다.
 * 거래의 상태·금액에 대한 가정은 TransactionRecord 에 적었다. 문서의 "요청이 60초까지 걸릴 수 있다"는 흉내 내지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class TransactionController {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 5000;

    private final ScenarioStore scenarioStore;

    @GetMapping("/v1/transactions")
    public List<TransactionResponse> transactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String startingAfter,
            @RequestParam(required = false) Integer limit) throws InterruptedException {
        scenarioStore.countTransactionList();
        scenarioStore.awaitTransactionAnswer();
        List<TransactionRecord> all = scenarioStore.transactionsBetween(startDate.atZone(KOREA).toInstant(),
                endDate.withNano(999_999_999).atZone(KOREA).toInstant());
        return all.stream()
                .skip(positionAfter(all, startingAfter))
                .limit(limitOf(limit))
                .map(TransactionResponse::from)
                .toList();
    }

    private static long positionAfter(List<TransactionRecord> all, String startingAfter) {
        if (Objects.isNull(startingAfter)) {
            return 0;
        }
        for (int position = 0; position < all.size(); position++) {
            if (all.get(position).getTransactionKey().equals(startingAfter)) {
                return position + 1L;
            }
        }
        return all.size();
    }

    private static int limitOf(Integer limit) {
        if (Objects.isNull(limit)) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
