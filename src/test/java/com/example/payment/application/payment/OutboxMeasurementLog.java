package com.example.payment.application.payment;

import com.example.payment.OrderServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 9장 측정의 기록 도구: 주문 서비스가 받은 이벤트로 유실·중복·순서 위반·주문 상태 어긋남·도착 지연을 세고, 결과를 build/measurement/ch09-outbox.txt 에 덧붙인다.
 * 모든 결제는 승인 뒤 전액 취소한 결제다 — 결제마다 승인(APPROVED)과 취소(APPROVAL_CANCELED) 두 이벤트가 가야 한다.
 */
class OutboxMeasurementLog {

    static final Path OUTPUT = Path.of("build", "measurement", "ch09-outbox.txt");
    static final List<String> EXPECTED = List.of("APPROVED", "APPROVAL_CANCELED");

    private OutboxMeasurementLog() {
    }

    static void write(String line) {
        try {
            Files.createDirectories(OUTPUT.getParent());
            Files.writeString(OUTPUT, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println("[ch09-outbox] " + line);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    // 주문들에 대해 받은 이벤트를 받은 순서대로 세어 한 줄로 적는다
    static String describe(OrderServer orderServer, Collection<String> orderIds) {
        int lost = 0;
        int duplicated = 0;
        int outOfOrder = 0;
        int wrongOrderStatus = 0;
        List<Long> delays = new ArrayList<>();
        for (String orderId : orderIds) {
            List<OrderServer.Receipt> receipts = orderServer.receipts(orderId);
            Set<String> seen = new HashSet<>();
            Map<String, OrderServer.Receipt> first = new HashMap<>();
            boolean canceled = false;
            boolean approvalAfterCancel = false;
            for (OrderServer.Receipt receipt : receipts) {
                if (receipt.status().equals("APPROVAL_CANCELED")) {
                    canceled = true;
                }
                if (canceled && receipt.status().equals("APPROVED")) {
                    approvalAfterCancel = true;
                }
                if (seen.add(receipt.status())) {
                    first.put(receipt.status(), receipt);
                } else {
                    duplicated++;
                }
            }
            lost += (int) EXPECTED.stream().filter(status -> Objects.isNull(first.get(status))).count();
            outOfOrder += approvalAfterCancel ? 1 : 0;
            wrongOrderStatus += "CANCELED".equals(orderServer.statusOf(orderId)) ? 0 : 1;
            first.values().forEach(receipt -> delays.add(receipt.delay().toMillis()));
        }
        return "이벤트 " + orderIds.size() * EXPECTED.size() + " | 유실 " + lost + " | 중복 " + duplicated + " | 순서 위반 결제 " + outOfOrder
                + " | 주문 상태 어긋남 " + wrongOrderStatus + " | 도착 지연 ms " + percentiles(delays);
    }

    static String percentiles(List<Long> values) {
        if (values.isEmpty()) {
            return "없음";
        }
        List<Long> sorted = values.stream().sorted().toList();
        return "중앙 " + sorted.get(sorted.size() / 2) + " · p95 " + sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1))
                + " · 최대 " + sorted.get(sorted.size() - 1);
    }

}
