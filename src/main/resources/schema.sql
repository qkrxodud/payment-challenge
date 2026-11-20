create table if not exists payment (
    id          bigint         not null auto_increment,
    order_id    varchar(64)    not null,
    amount      decimal(19, 2) not null,
    status      varchar(30)    not null,
    payment_key varchar(200),
    -- 4장: 승인을 요청하려고 인증한 때. 보정은 상태와 이 시각으로 대상을 고른다
    authenticated_at datetime(6),
    -- 8장: 상품을 담아 준비한 결제의 주문자, 쓴 쿠폰, 쓴 포인트. 금액만 받아 준비한 결제에는 주문자·쿠폰이 없고 포인트는 0 이다
    member_id   varchar(64),
    coupon_id   bigint,
    point_amount decimal(19, 2) not null default 0,
    primary key (id),
    unique key uk_payment_order_id (order_id),
    key idx_payment_status_authenticated_at (status, authenticated_at)
);

-- 5장: 취소 요청 한 건마다 한 줄. 결제의 남은 금액은 끝난 취소의 합으로 계산한다
-- 6장: 카드사에서만 일어난 취소(가맹점 관리 화면 취소 등)도 한 줄로 들여온다. 그 줄에는 고객 키와 요청 비교값이 없다
create table if not exists payment_cancel (
    id              bigint         not null auto_increment,
    payment_id      bigint         not null,
    idempotency_key varchar(300),
    -- 같은 키로 다시 온 요청이 처음과 같은 내용인지 비교할 값(요청 금액·사유의 SHA-256)
    request_digest  char(64),
    amount          decimal(19, 2) not null,
    reason          varchar(200)   not null,
    status          varchar(20)    not null,
    -- 카드사가 취소 건마다 주는 키(토스페이먼츠 transactionKey)
    transaction_key varchar(200),
    -- 이 취소가 끝난 뒤 남은 금액. 같은 키로 다시 온 요청에 처음과 같은 답을 주려고 남긴다
    balance_after   decimal(19, 2),
    -- 카드사에 취소를 요청하려고 기록한 때. 보정은 상태와 이 시각으로 대상을 고른다
    requested_at    datetime(6)    not null,
    primary key (id),
    unique key uk_payment_cancel_idempotency_key (payment_id, idempotency_key),
    -- 6장: 카드사 취소 한 건은 한 번만 들여온다. 같은 알림을 동시에 두 번 처리해도 두 번째 기록은 막힌다
    unique key uk_payment_cancel_transaction_key (payment_id, transaction_key),
    key idx_payment_cancel_status_requested_at (status, requested_at),
    constraint fk_payment_cancel_payment foreign key (payment_id) references payment (id)
);

-- 6장: 받은 웹훅 한 건마다 한 줄. 받자마자 남기고 200 으로 답한 뒤, 처리기가 카드사 조회로 결제를 맞춘다.
-- 모르는 주문의 알림도 남기므로 결제를 가리키지 않는다(주문번호만 둔다). 본문의 상태·시각은 받은 그대로 기록으로만 남긴다
create table if not exists payment_webhook (
    id                  bigint       not null auto_increment,
    order_id            varchar(64)  not null,
    event_type          varchar(50)  not null,
    reported_status     varchar(30),
    reported_created_at varchar(40),
    -- RECEIVED(처리 전), PROCESSED(카드사 조회로 맞춤), IGNORED(모르는 주문)
    status              varchar(20)  not null,
    -- 카드사 조회에 실패한 횟수. 실패한 알림은 RECEIVED 로 남아 다음 처리 때 다시 본다
    failed_lookups      int          not null,
    received_at         datetime(6)  not null,
    processed_at        datetime(6),
    primary key (id),
    key idx_payment_webhook_status_id (status, id)
);

-- 7장: 주기 작업(승인·취소 보정, 받은 알림 처리)마다 한 줄. 서버 여러 대 가운데 한 번에 한 대만 그 작업을 돌린다.
-- 작업을 맡은 서버(locked_by)가 locked_until 까지 맡는다. 다 돌면 locked_until 을 그때로 당겨 다음 주기에 아무 서버나 맡게 하고,
-- 맡은 서버가 죽으면 locked_until 이 지난 뒤에 다른 서버가 맡는다
create table if not exists job_lock (
    name         varchar(64)  not null,
    locked_until datetime(6)  not null,
    locked_by    varchar(100),
    primary key (name)
);

insert ignore into job_lock (name, locked_until) values ('payment-reconciliation', '1970-01-01 00:00:00');
insert ignore into job_lock (name, locked_until) values ('payment-webhook', '1970-01-01 00:00:00');
-- 9장 결정 3: 남긴 결제 결과를 보내는 릴레이도 한 대만 돈다
insert ignore into job_lock (name, locked_until) values ('payment-outbox-relay', '1970-01-01 00:00:00');

-- 8장: 상품. 10장부터 재고는 재고 서비스(inventory-service)의 DB 에 있다
create table if not exists product (
    id    bigint         not null auto_increment,
    name  varchar(100)   not null,
    price decimal(19, 2) not null,
    primary key (id)
);

-- 8장: 결제에 담긴 상품. 준비할 때의 상품가를 함께 남긴다(나중에 상품가가 바뀌어도 이 결제의 금액은 그대로다)
create table if not exists payment_item (
    id         bigint         not null auto_increment,
    payment_id bigint         not null,
    product_id bigint         not null,
    quantity   int            not null,
    unit_price decimal(19, 2) not null,
    primary key (id),
    constraint fk_payment_item_payment foreign key (payment_id) references payment (id)
);

-- 8장: 회원의 포인트 잔액(회원마다 한 줄)
create table if not exists point_wallet (
    member_id varchar(64)    not null,
    balance   decimal(19, 2) not null,
    primary key (member_id)
);

-- 8장: 회원에게 발급한 정액 할인 쿠폰. 쓰면 USED 와 쓴 결제를 남긴다
create table if not exists coupon (
    id              bigint         not null auto_increment,
    member_id       varchar(64)    not null,
    discount_amount decimal(19, 2) not null,
    -- ISSUED(쓸 수 있음), USED(결제에 쓰임)
    status          varchar(20)    not null,
    payment_id      bigint,
    primary key (id)
);

-- 8장 결정 4: 포인트 증감 이력. 결제 하나에 쓰기(USE)와 되돌리기(RESTORE)는 한 번씩만 올라간다 —
-- 같은 실패를 여러 곳이 닫으려 해도 두 번째 되돌리기는 이 키에 막혀 잔액을 다시 늘리지 않는다
create table if not exists point_history (
    id         bigint         not null auto_increment,
    member_id  varchar(64)    not null,
    payment_id bigint         not null,
    -- USE(결제에 씀), RESTORE(결제가 승인 없이 끝나거나 전액 취소돼 돌려줌)
    type       varchar(20)    not null,
    amount     decimal(19, 2) not null,
    created_at datetime(6)    not null,
    primary key (id),
    unique key uk_point_history_payment_type (payment_id, type)
);

-- 9장 결정 1: 주문 서비스에 알릴 결제 결과. 결제 상태를 바꾼 그 트랜잭션에서 한 줄 남긴다 — 상태가 커밋되면 이 줄도 남고,
-- 커밋이 실패하면 함께 남지 않는다. 보내는 일은 따로 한다. 번호(id)가 이벤트의 고유 번호이고, 같은 결제의 이벤트는 번호 순이 만든 순서다.
-- 결제를 가리키는 외래 키는 두지 않는다(결제 기록과 따로 보내고 지우는 기록이다)
create table if not exists payment_outbox (
    id          bigint      not null auto_increment,
    payment_id  bigint      not null,
    order_id    varchar(64) not null,
    status      varchar(30) not null,
    -- 10장 결정 5: PAYMENT_RESULT(결제 결과, 주문 서비스에 알림), STOCK_CONFIRM·STOCK_RELEASE(재고 서비스에 보내는 예약 확정·해제)
    type        varchar(30) not null default 'PAYMENT_RESULT',
    occurred_at datetime(6) not null,
    -- 브로커가 받았다고 답한 때. 아직 보내지 않았으면 비어 있다
    sent_at     datetime(6),
    primary key (id),
    key idx_payment_outbox_sent_at_id (sent_at, id)
);
