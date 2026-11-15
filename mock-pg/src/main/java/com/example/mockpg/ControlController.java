package com.example.mockpg;

import com.example.mockpg.dto.ScenarioRequest;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 따로 띄운 가짜 PG(부하 테스트 등)를 밖에서 조작하는 API */
@RestController
@RequiredArgsConstructor
public class ControlController {

    private final ScenarioStore scenarioStore;

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
}
