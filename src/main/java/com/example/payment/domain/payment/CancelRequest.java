package com.example.payment.domain.payment;

import com.example.payment.domain.error.exception.InvalidCancelReasonException;
import com.example.payment.domain.error.exception.InvalidIdempotencyKeyException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 고객이 보낸 취소 요청 한 건(5장): Idempotency-Key, 요청한 금액(없으면 남은 금액 전부), 사유.
 * 같은 키로 다시 온 요청이 처음과 같은 내용인지 비교할 값(requestDigest)을 만든다. 금액은 요청에 적힌 대로 비교한다 —
 * 금액 없이 온 요청을 "그때의 남은 금액"으로 바꿔 비교하면, 다시 온 같은 요청이 그사이 줄어든 남은 금액 때문에 다른 요청이 된다.
 */
@Getter
public class CancelRequest {

    // 토스페이먼츠 멱등키의 최대 길이. 이 키를 카드사에도 그대로 넘긴다
    static final int MAX_KEY_LENGTH = 300;

    private final String idempotencyKey;
    private final BigDecimal requestedAmount;
    private final String reason;

    @Builder(access = AccessLevel.PRIVATE)
    private CancelRequest(String idempotencyKey, BigDecimal requestedAmount, String reason) {
        this.idempotencyKey = idempotencyKey;
        this.requestedAmount = requestedAmount;
        this.reason = reason;
    }

    public static CancelRequest of(String orderId, String idempotencyKey, BigDecimal requestedAmount, String reason) {
        if (isInvalidKey(idempotencyKey)) {
            throw new InvalidIdempotencyKeyException(orderId);
        }
        if (isBlank(reason)) {
            throw new InvalidCancelReasonException(orderId);
        }
        return CancelRequest.builder()
                .idempotencyKey(idempotencyKey)
                .requestedAmount(requestedAmount)
                .reason(reason)
                .build();
    }

    // 요청 내용(금액, 사유)을 한 값으로 줄인다. 15000 과 15000.00 은 같은 금액으로 본다
    public String requestDigest() {
        String amount = Objects.isNull(requestedAmount) ? "" : requestedAmount.stripTrailingZeros().toPlainString();
        return sha256(amount + "\n" + reason);
    }

    private static boolean isInvalidKey(String idempotencyKey) {
        return isBlank(idempotencyKey) || idempotencyKey.length() > MAX_KEY_LENGTH;
    }

    private static boolean isBlank(String text) {
        return Objects.isNull(text) || text.isBlank();
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is not available.", missing);
        }
    }
}
