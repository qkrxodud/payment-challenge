package com.example.payment.infrastructure.pg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;

import com.example.payment.domain.payment.PaymentInquiry;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("카드사 결제 조회 답 읽기")
class PgPaymentStatusTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("approvedStates")
    @DisplayName("카드사에 승인이 남은 결제는 승인으로 읽는다")
    void readsApproved(String status) {
        assertThat(PgPaymentStatus.inquiryOf(status)).isEqualTo(PaymentInquiry.APPROVED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notApprovedStates")
    @DisplayName("카드사에 승인이 남지 않은 결제는 승인 없음으로 읽는다")
    void readsNotApproved(String status) {
        assertThat(PgPaymentStatus.inquiryOf(status)).isEqualTo(PaymentInquiry.NOT_APPROVED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("undecidedStates")
    @DisplayName("카드사도 아직 처리 중이거나 처음 보는 답이면 결과를 정하지 않는다")
    void readsUndecided(String status) {
        assertThat(PgPaymentStatus.inquiryOf(status)).isEqualTo(PaymentInquiry.UNDECIDED);
    }

    static Stream<Named<String>> approvedStates() {
        return Stream.of(
                named("승인 완료", "DONE"),
                named("부분 취소(승인은 남음)", "PARTIAL_CANCELED"));
    }

    static Stream<Named<String>> notApprovedStates() {
        return Stream.of(
                named("승인 실패", "ABORTED"),
                named("승인 뒤 취소", "CANCELED"),
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
