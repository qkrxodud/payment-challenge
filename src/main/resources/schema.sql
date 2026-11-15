create table if not exists payment (
    id          bigint         not null auto_increment,
    order_id    varchar(64)    not null,
    amount      decimal(19, 2) not null,
    status      varchar(30)    not null,
    payment_key varchar(200),
    -- 4장: 승인을 요청하려고 인증한 때. 보정은 상태와 이 시각으로 대상을 고른다
    authenticated_at datetime(6),
    primary key (id),
    unique key uk_payment_order_id (order_id),
    key idx_payment_status_authenticated_at (status, authenticated_at)
);

-- 5장: 취소 요청 한 건마다 한 줄. 결제의 남은 금액은 끝난 취소의 합으로 계산한다
create table if not exists payment_cancel (
    id              bigint         not null auto_increment,
    payment_id      bigint         not null,
    idempotency_key varchar(300)   not null,
    amount          decimal(19, 2) not null,
    reason          varchar(200)   not null,
    status          varchar(20)    not null,
    -- 카드사가 취소 건마다 주는 키(토스페이먼츠 transactionKey)
    transaction_key varchar(200),
    primary key (id),
    constraint fk_payment_cancel_payment foreign key (payment_id) references payment (id)
);
