package com.example.payment.application.payment;

/** 11장 측정: 운영 — blocking 다시 시도(1·2·4·8초) + DLT */
class OperationalConsumerMeasurement extends ConsumerMeasurement {

    @Override
    String method() {
        return "운영 — blocking 다시 시도(1·2·4·8초) + DLT";
    }
}
