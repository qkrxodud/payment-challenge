package com.example.payment.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** 4장 PgPaymentStatusTest 를 대신한다. 6장부터 카드사 CANCELED 는 "승인이 있었던 결제"로 읽는다(4장은 승인 없음) */
@DisplayName("카드사 결제 상태 읽기")
class GatewayStatusTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedStates")
    @DisplayName("카드사가 한 번은 승인한 결제는 승인이 있었던 것으로 읽는다")
    void readsApproval(String status) {
        assertThat(GatewayStatus.of(status).hasApproval()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declinedStates")
    @DisplayName("카드사가 승인하지 않고 끝낸 결제는 거절로 읽는다")
    void readsDeclined(String status) {
        assertThat(GatewayStatus.of(status).isDeclined()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecidedStates")
    @DisplayName("카드사도 아직 처리 중이거나 처음 보는 답이면 승인으로도 거절로도 읽지 않는다")
    void readsUndecided(String status) {
        GatewayStatus read = GatewayStatus.of(status);

        assertThat(read.hasApproval() || read.isDeclined()).isFalse();
    }

    static Stream<Named<String>> approvedStates() {
        return Stream.of(
                named("승인 완료", "DONE"),
                named("부분 취소(승인은 남음)", "PARTIAL_CANCELED"),
                named("승인 뒤 취소", "CANCELED"));
    }

    static Stream<Named<String>> declinedStates() {
        return Stream.of(
                named("승인 실패", "ABORTED"),
                named("유효 시간이 지나 거래 취소", "EXPIRED"));
    }

    static Stream<Named<String>> undecidedStates() {
        return Stream.of(
                named("결제 생성", "READY"),
                named("인증을 마치고 승인 전", "IN_PROGRESS"),
                named("입금 대기", "WAITING_FOR_DEPOSIT"),
                named("목록에 없는 답", "SOMETHING_NEW"));
    }
}
