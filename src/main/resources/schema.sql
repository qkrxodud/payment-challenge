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
