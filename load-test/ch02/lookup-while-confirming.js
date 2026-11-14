// 2장: PG 답을 기다리는 승인 요청이 몰리는 동안 다른 결제 조회가 얼마나 걸리는지 잰다.
// 승인: 20 VU 가 쉬지 않고 결제를 준비하고 승인한다(Mock PG 는 1초 뒤에 답하도록 미리 맞춘다).
// 조회: 초당 20건, 미리 준비해 둔 결제 하나를 조회한다.
import http from 'k6/http';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080/api/v1/payments';
const DURATION = __ENV.DURATION || '30s';
const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

const lookupDuration = new Trend('lookup_duration', true);
const lookupFailed = new Rate('lookup_failed');
const confirmDuration = new Trend('confirm_duration', true);
const confirmFailed = new Rate('confirm_failed');

export const options = {
  scenarios: {
    confirm: { executor: 'constant-vus', exec: 'confirm', vus: 20, duration: DURATION },
    lookup: {
      executor: 'constant-arrival-rate', exec: 'lookup',
      rate: 20, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 400,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const orderId = `lookup-${Date.now()}`;
  http.post(BASE, JSON.stringify({ orderId, amount: 15000 }), JSON_HEADERS);
  return { orderId };
}

export function confirm() {
  const orderId = `confirm-${__VU}-${__ITER}-${Date.now()}`;
  http.post(BASE, JSON.stringify({ orderId, amount: 15000 }), JSON_HEADERS);
  const res = http.post(`${BASE}/${orderId}/confirm`, JSON.stringify({ paymentKey: `key-${orderId}` }), JSON_HEADERS);
  confirmDuration.add(res.timings.duration);
  confirmFailed.add(res.status !== 200);
}

export function lookup(data) {
  const res = http.get(`${BASE}/${data.orderId}`);
  lookupDuration.add(res.timings.duration);
  lookupFailed.add(res.status !== 200);
}
