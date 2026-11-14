create table if not exists payment (
    id       bigint         not null auto_increment,
    order_id varchar(64)    not null,
    amount   decimal(19, 2) not null,
    status   varchar(30)    not null,
    primary key (id),
    unique key uk_payment_order_id (order_id)
);
