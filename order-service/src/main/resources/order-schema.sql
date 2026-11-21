-- 9장: 주문. 결제 이벤트가 처음 오면 남기고, 그 뒤로는 받은 결제 결과대로 상태만 바꾼다
create table if not exists orders (
    id             bigint      not null auto_increment,
    order_id       varchar(64) not null,
    -- PAID(결제 완료), PAYMENT_FAILED(결제 실패), CANCELED(결제 취소)
    status         varchar(30) not null,
    payment_status varchar(30) not null,
    updated_at     datetime(6) not null,
    primary key (id),
    unique key uk_orders_order_id (order_id)
);

-- 9장: 받은 결제 이벤트 한 건마다 한 줄.
-- 11장 결정 1: 고유 번호(event_id, 결제 서버 payment_outbox 번호)는 두 번 오를 수 없다 — 같은 이벤트가 다시 오면 남지 않고 주문도 바꾸지 않는다.
-- 9장에 만든 표에는 같은 번호 줄이 이미 있을 수 있어, 그 줄을 지운 뒤에 고유 키를 더해야 한다(시험은 새 스키마로 만든다)
create table if not exists received_payment_event (
    id             bigint      not null auto_increment,
    event_id       varchar(64) not null,
    order_id       varchar(64) not null,
    payment_status varchar(30) not null,
    occurred_at    datetime(6) not null,
    partition_no   int         not null,
    offset_no      bigint      not null,
    received_at    datetime(6) not null,
    primary key (id),
    unique key uk_received_payment_event_event_id (event_id),
    key idx_received_payment_event_order_id (order_id, id)
);
