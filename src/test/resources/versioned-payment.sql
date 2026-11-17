-- 7장 결정 1 비교(실제 JPA @Version) 시험 DB 에만 더하는 변경 번호 칸
alter table payment add column version bigint not null default 0;
