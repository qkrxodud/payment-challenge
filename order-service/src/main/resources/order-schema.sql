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

-- 9장: 받은 결제 이벤트 한 건마다 한 줄. 같은 이벤트가 다시 와도 한 줄 더 남는다(중복을 셀 수 있게, 가리는 일은 11장)
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
    key idx_received_payment_event_order_id (order_id, id)
);
