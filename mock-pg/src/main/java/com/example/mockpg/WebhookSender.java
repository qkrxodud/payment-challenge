package com.example.mockpg;

import com.example.mockpg.dto.PaymentResponse;
import com.example.mockpg.dto.WebhookBody;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 6장: 결제 상태가 바뀔 때 상점 서버로 PAYMENT_STATUS_CHANGED 웹훅을 보낸다. 토스페이먼츠 문서에서 확인한 동작만 흉내 낸다:
 * - 10초(answerTimeout) 안에 200 으로 답하면 성공, 아니면 최대 7회 재전송한다(간격은 설정값, 기본은 토스페이먼츠 간격)
 * - 본문은 eventType, createdAt, data(상태가 바뀐 Payment 객체)이고 eventId 는 없다
 * 문서에서 확인하지 못해 가정한 것:
 * - 알림은 상태가 바뀐 순서대로 바로 보낸다. 문서에 순서 보장 문구가 없어, 시험에서는 쌓아 두었다가 순서를 바꿔 보낸다(held)
 * - 재전송 본문은 처음 본문과 같다(createdAt 도 같다)
 * - createdAt 은 한국 시각이다(문서 형식에 시간대가 없다)
 * - 서명 헤더(tosspayments-webhook-signature)는 붙이지 않는다. 결제 상태 알림에도 붙는지 문서상 분명하지 않아 다루지 않는다
 * 상태를 두는 것 자체가 목적인 시험 도구라 스레드 안전 저장소만 쓴다.
 */
@Component
@RequiredArgsConstructor
public class WebhookSender {

    static final String PAYMENT_STATUS_CHANGED = "PAYMENT_STATUS_CHANGED";
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "mock-pg-webhook");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<WebhookSettings> settings = new AtomicReference<>(WebhookSettings.off());
    private final AtomicLong generation = new AtomicLong();
    private final List<WebhookDelivery> deliveries = new CopyOnWriteArrayList<>();
    private final List<WebhookDelivery> held = Collections.synchronizedList(new ArrayList<>());

    public void change(WebhookSettings next) {
        settings.set(next);
    }

    public WebhookSettings current() {
        return settings.get();
    }

    // 웹훅을 끄고 쌓인 알림과 기록을 지운다. 앞 세대 알림의 재전송도 멈춘다
    public void reset() {
        generation.incrementAndGet();
        settings.set(WebhookSettings.off());
        deliveries.clear();
        held.clear();
    }

    // 결제 상태가 바뀌었다. 바뀐 Payment 객체를 담아 알린다
    public void statusChanged(PaymentRecord record) {
        send(record.getOrderId(), PaymentResponse.from(record));
    }

    // 시험용: 카드사 기록과 다른 상태를 담은 위조 알림을 보낸다
    public void forge(String orderId, String status, BigDecimal amount) {
        send(orderId, PaymentResponse.forged(orderId, status, amount));
    }

    // 쌓아 둔 알림을 보낸다. reversed 면 늦게 생긴 알림부터 보낸다
    public void releaseHeld(boolean reversed) {
        List<WebhookDelivery> released;
        synchronized (held) {
            released = new ArrayList<>(held);
            held.clear();
        }
        if (reversed) {
            Collections.reverse(released);
        }
        WebhookSettings now = settings.get();
        released.forEach(delivery -> dispatch(delivery, now.getDelay()));
    }

    // 보낸 알림이 모두 200 을 받았거나 재전송을 다 썼다(쌓아 둔 알림이 없을 때)
    public boolean isSettled() {
        return held.isEmpty() && deliveries.stream().allMatch(WebhookDelivery::isSettled);
    }

    public List<WebhookDelivery> deliveriesOf(String orderId) {
        return deliveries.stream()
                .filter(delivery -> delivery.getOrderId().equals(orderId))
                .toList();
    }

    public List<WebhookDelivery> deliveries() {
        return List.copyOf(deliveries);
    }

    private void send(String orderId, PaymentResponse data) {
        WebhookSettings now = settings.get();
        if (now.isOff()) {
            return;
        }
        String body = jsonMapper.writeValueAsString(
                new WebhookBody(PAYMENT_STATUS_CHANGED, LocalDateTime.now(KOREA).format(CREATED_AT), data));
        int copies = now.isDuplicated() ? 2 : 1;
        for (int copy = 0; copy < copies; copy++) {
            WebhookDelivery delivery = WebhookDelivery.of(generation.get(), orderId, data.status(), body);
            deliveries.add(delivery);
            queueOrDispatch(delivery, now);
        }
    }

    private void queueOrDispatch(WebhookDelivery delivery, WebhookSettings now) {
        if (now.isHeld()) {
            held.add(delivery);
            return;
        }
        dispatch(delivery, now.getDelay());
    }

    private void dispatch(WebhookDelivery delivery, Duration after) {
        executor.schedule(() -> attempt(delivery, 0), after.toMillis(), TimeUnit.MILLISECONDS);
    }

    // 한 번 보내고, 200 이 아니면 정한 간격 뒤에 다시 보낸다. 재전송을 다 쓰면 그만둔다
    private void attempt(WebhookDelivery delivery, int retried) {
        if (delivery.getGeneration() != generation.get()) {
            delivery.settle();
            return;
        }
        WebhookSettings now = settings.get();
        WebhookAttempt attempt = post(delivery, now);
        delivery.record(attempt);
        if (attempt.isOk() || retried >= now.getRetryIntervals().size()) {
            delivery.settle();
            return;
        }
        executor.schedule(() -> attempt(delivery, retried + 1),
                now.getRetryIntervals().get(retried).toMillis(), TimeUnit.MILLISECONDS);
    }

    private WebhookAttempt post(WebhookDelivery delivery, WebhookSettings now) {
        long started = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(now.getTargetUrl()))
                    .timeout(now.getAnswerTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.getBody()))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return WebhookAttempt.of(response.statusCode(), elapsedMillis(started));
        } catch (IOException noAnswer) {
            return WebhookAttempt.of(WebhookAttempt.NO_ANSWER, elapsedMillis(started));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return WebhookAttempt.of(WebhookAttempt.NO_ANSWER, elapsedMillis(started));
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
