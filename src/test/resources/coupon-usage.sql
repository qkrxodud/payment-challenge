-- 8장 결정 5 비교(사용 기록) 시험 DB 에만 두는 쿠폰 사용 기록. 쿠폰 하나는 한 줄만 올라간다
create table if not exists coupon_usage (
    coupon_id  bigint not null,
    payment_id bigint not null,
    primary key (coupon_id)
);
