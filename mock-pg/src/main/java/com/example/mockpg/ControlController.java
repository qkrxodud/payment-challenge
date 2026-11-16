package com.example.mockpg;

import com.example.mockpg.dto.ConsoleCancelRequest;
import com.example.mockpg.dto.ForgedWebhookRequest;
import com.example.mockpg.dto.ScenarioRequest;
import com.example.mockpg.dto.WebhookSettingsRequest;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 따로 띄운 가짜 PG(부하 테스트 등)를 밖에서 조작하는 API. 6장부터 웹훅 설정·쌓인 알림 보내기·위조 알림·관리 화면 취소를 더한다 */
@RestController
@RequiredArgsConstructor
public class ControlController {

    private final ScenarioStore scenarioStore;
    private final WebhookSender webhookSender;
    private final MerchantConsole merchantConsole;

    @PutMapping("/control/scenario")
    public void change(@RequestBody ScenarioRequest request) {
        scenarioStore.change(Scenario.of(Duration.ofMillis(request.delayMillis()), request.outcome(),
                request.followUpDelay()));
    }

    @DeleteMapping("/control/scenario")
    public void reset() {
        scenarioStore.reset();
    }

    @GetMapping("/control/approvals/{orderId}")
    public int approvalCount(@PathVariable String orderId) {
        return scenarioStore.approvalCount(orderId);
    }

    @GetMapping("/control/cancels/{orderId}")
    public int cancelCount(@PathVariable String orderId) {
        return scenarioStore.cancelCount(orderId);
    }

    // 6장: 웹훅을 받을 주소와 시험용 조작(쌓아 두기, 두 번 보내기, 늦게 보내기, 재전송 간격)
    @PutMapping("/control/webhook")
    public void changeWebhook(@RequestBody WebhookSettingsRequest request) {
        webhookSender.change(request.toSettings());
    }

    @DeleteMapping("/control/webhook")
    public void resetWebhook() {
        webhookSender.reset();
    }

    @PostMapping("/control/webhook/release")
    public void releaseHeld(@RequestParam(defaultValue = "false") boolean reversed) {
        webhookSender.releaseHeld(reversed);
    }

    @PostMapping("/control/webhook/forged")
    public void forge(@RequestBody ForgedWebhookRequest request) {
        webhookSender.forge(request.orderId(), request.status(), request.amount());
    }

    // 이 주문에 보낸 알림마다 받은 상태 코드(답이 없으면 0)
    @GetMapping("/control/webhook/{orderId}")
    public List<List<Integer>> deliveries(@PathVariable String orderId) {
        return webhookSender.deliveriesOf(orderId).stream()
                .map(delivery -> delivery.getAttempts().stream().map(WebhookAttempt::getStatusCode).toList())
                .toList();
    }

    @PostMapping("/control/console-cancels/{orderId}")
    public void cancelOnConsole(@PathVariable String orderId, @RequestBody ConsoleCancelRequest request) {
        merchantConsole.cancel(orderId, request.cancelAmount(), request.cancelReason());
    }
}
