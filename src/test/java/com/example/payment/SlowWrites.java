package com.example.payment;

import static org.awaitility.Awaitility.await;

import com.example.payment.domain.payment.PaymentStatus;
import java.time.Duration;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 7장: 동시 요청이 겹치는 순간을 실행마다 같게 고정하는 시험 도구.
 * MySQL 트리거로 먼저 보낸 요청의 기록 한 건을 1초 붙잡아 두고(그 요청은 그 줄의 잠금을 쥔 채 잠든다),
 * 잠든 것을 확인한 뒤에 다음 요청을 보낸다. 다음 요청은 앞 요청이 기록을 끝내기(커밋) 전에 결제를 읽는다.
 * 2·4·5장에서 기록 실패를 트리거로 만든 것과 같은 방법이다. 시험이 끝나면 release 로 트리거를 지운다.
 */
public class SlowWrites {

    // 다음 요청이 앞 요청의 기록이 끝나기 전에 결제를 읽기에 넉넉한 시간
    public static final Duration HOLD = Duration.ofSeconds(1);

    private final JdbcTemplate jdbcTemplate;

    public SlowWrites(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 결제 상태를 이 상태로 바꾸는 기록을 붙잡는다. 바꾸는 줄의 잠금을 쥔 채 잠든다
    public void holdPaymentChangeTo(PaymentStatus status) {
        jdbcTemplate.execute("create trigger hold_payment_update before update on payment for each row\n"
                + "begin\n"
                + "    if new.status = '" + status.name() + "' and old.status <> new.status then\n"
                + "        do sleep(" + HOLD.toSeconds() + ");\n"
                + "    end if;\n"
                + "end");
    }

    // 새 결제를 남기는 기록을 붙잡는다. 같은 주문번호를 남기려는 다른 기록은 그동안 기다린다
    public void holdNewPayment() {
        jdbcTemplate.execute("create trigger hold_payment_insert after insert on payment for each row\n"
                + "begin\n"
                + "    do sleep(" + HOLD.toSeconds() + ");\n"
                + "end");
    }

    // 카드사에 보내기 전에 남기는 새 취소(요청 상태)를 붙잡는다
    public void holdNewCancel() {
        jdbcTemplate.execute("create trigger hold_cancel_insert after insert on payment_cancel for each row\n"
                + "begin\n"
                + "    if new.status = 'REQUESTED' then\n"
                + "        do sleep(" + HOLD.toSeconds() + ");\n"
                + "    end if;\n"
                + "end");
    }

    // 붙잡힌 기록이 잠들 때까지 기다린다
    public void awaitHeld() {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(10)).until(this::isHolding);
    }

    private boolean isHolding() {
        Integer sleeping = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.processlist where state = 'User sleep'", Integer.class);
        return Objects.requireNonNullElse(sleeping, 0) > 0;
    }

    public void release() {
        jdbcTemplate.execute("drop trigger if exists hold_payment_update");
        jdbcTemplate.execute("drop trigger if exists hold_payment_insert");
        jdbcTemplate.execute("drop trigger if exists hold_cancel_insert");
    }
}
