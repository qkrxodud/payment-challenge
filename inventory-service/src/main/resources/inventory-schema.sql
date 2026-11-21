-- 10장: 상품마다 한 줄인 재고(8장에 결제 서버에 있던 표를 옮겨 왔다). 상품(이름·가격)은 결제 서버에 남는다
create table if not exists stock (
    product_id bigint not null,
    quantity   int    not null,
    primary key (product_id)
);

-- 10장 결정 3: 결제 하나가 잡은 재고(예약) 한 줄. 예약 번호는 결제 번호다(결제 하나에 예약 하나).
-- RESERVED(잡음) → CONFIRMED(결제 승인으로 확정) / RELEASED(돌려줌). 확정된 예약도 결제가 전액 취소되면 RELEASED 가 된다.
-- 해제가 예약보다 먼저 오면 담긴 상품 없이 RELEASED 줄을 먼저 남긴다 — 그 뒤 늦게 온 예약은 이 줄에 막혀 받지 않는다
create table if not exists stock_reservation (
    reservation_id bigint      not null,
    status         varchar(20) not null,
    -- 11장 결정 2: 마지막으로 반영한 재고 명령의 번호(결제 서버 payment_outbox 번호). 이보다 번호가 크지 않은 명령은 반영하지 않는다.
    -- HTTP 로 잡은 예약은 아직 비어 있다
    last_command_id bigint     null,
    created_at     datetime(6) not null,
    updated_at     datetime(6) not null,
    primary key (reservation_id)
);

-- 10장 결정 3: 예약이 잡은 상품과 수량. 돌려줄 때 이 수량만큼 돌려준다
create table if not exists stock_reservation_item (
    id             bigint not null auto_increment,
    reservation_id bigint not null,
    product_id     bigint not null,
    quantity       int    not null,
    primary key (id),
    key idx_stock_reservation_item_reservation_id (reservation_id)
);

-- 11장 결정 1: 처리한 재고 명령 한 건마다 한 줄. 명령 번호(command_id, 결제 서버 payment_outbox 번호)는 두 번 오를 수 없다 —
-- 예약을 바꾼 그 트랜잭션에서 남겨, 같은 번호가 다시 오면 아무것도 바꾸지 않는다
create table if not exists processed_stock_command (
    id             bigint      not null auto_increment,
    command_id     varchar(64) not null,
    reservation_id bigint      not null,
    -- CONFIRM(확정), RELEASE(해제)
    command        varchar(20) not null,
    processed_at   datetime(6) not null,
    primary key (id),
    unique key uk_processed_stock_command_command_id (command_id)
);
