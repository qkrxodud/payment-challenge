-- 10장: 상품마다 한 줄인 재고(8장에 결제 서버에 있던 표를 옮겨 왔다). 상품(이름·가격)은 결제 서버에 남는다
create table if not exists stock (
    product_id bigint not null,
    quantity   int    not null,
    primary key (product_id)
);
