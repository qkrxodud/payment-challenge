-- 11장 결정 1 비교(가리지 않음·상태 전이만): 받은 결제 이벤트의 고유 번호가 두 번 오를 수 있게 9장처럼 고유 키를 뗀다.
-- 다시 넣기 모드로 같은 스키마에 다시 뜰 때는 이미 떼어 실패한다(spring.sql.init.continue-on-error=true 로 넘어간다)
alter table received_payment_event drop index uk_received_payment_event_event_id;
