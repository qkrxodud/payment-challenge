package com.example.payment;

import com.example.payment.domain.error.ErrorCode;
import com.example.payment.domain.error.PaymentChallengeException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 7장: 동시에 보낸 요청 하나의 결과. 답(result)이나, 받지 않은 이유(failure) 가운데 하나를 담는다.
 * 우리 오류(PaymentChallengeException)가 아닌 문제는 전역 오류 처리기가 내부 오류(500)로 답하므로 INTERNAL_ERROR 로 읽는다.
 */
public class Attempt<T> {

    private final T result;
    private final RuntimeException failure;

    private Attempt(T result, RuntimeException failure) {
        this.result = result;
        this.failure = failure;
    }

    public static <T> Attempt<T> of(Supplier<T> call) {
        try {
            return new Attempt<>(call.get(), null);
        } catch (RuntimeException refused) {
            return new Attempt<>(null, refused);
        }
    }

    public static <T> CompletableFuture<Attempt<T>> start(Supplier<T> call) {
        return CompletableFuture.supplyAsync(() -> of(call));
    }

    public static <T> CompletableFuture<Attempt<T>> start(Supplier<T> call, Executor executor) {
        return CompletableFuture.supplyAsync(() -> of(call), executor);
    }

    public T result() {
        return result;
    }

    public RuntimeException failure() {
        return failure;
    }

    public boolean isAnswered() {
        return Objects.isNull(failure);
    }

    // 받지 않았으면 그 오류 코드, 답했으면 비어 있다
    public ErrorCode errorCode() {
        if (isAnswered()) {
            return null;
        }
        if (failure instanceof PaymentChallengeException refused) {
            return refused.getErrorCode();
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    public boolean isServerError() {
        return errorCode() == ErrorCode.INTERNAL_ERROR;
    }
}
