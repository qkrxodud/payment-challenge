package com.example.mockpg.dto;

import com.example.mockpg.Outcome;

public record ScenarioRequest(long delayMillis, Outcome outcome) {
}
