-- 8장 결정 3 비교(변경 번호) 시험 DB 에만 더하는 재고 변경 번호 칸
alter table stock add column version bigint not null default 0;
