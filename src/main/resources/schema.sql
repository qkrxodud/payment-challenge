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
