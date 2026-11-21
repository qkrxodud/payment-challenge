package com.example.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 11장: 받는 쪽(주문 서비스·재고 서비스) DB 의 일시 장애를 만든다. MySQL 을 잠시 읽기 전용(read_only)으로 돌린다 — 그동안 받는 쪽 계정의 쓰기는
 * 곧바로 실패한다(DB 를 다른 서버로 넘기는 동안 옛 서버가 쓰기를 거부하는 것과 같은 오류). 관리자 계정은 막히지 않아 되돌릴 수 있다.
 * 연결을 끊는 장애(연결 풀이 새 연결을 기다림)는 이 도구로 만들지 않는다. 받는 쪽 둘과 결제 서버가 같은 MySQL 컨테이너를 쓰므로 함께 막힌다.
 */
public class ReceiverOutage {

    private final JdbcTemplate root;

    private ReceiverOutage(JdbcTemplate root) {
        this.root = root;
    }

    public static ReceiverOutage of(MySQLContainer mysql) {
        String serverUrl = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/");
        return new ReceiverOutage(new JdbcTemplate(new DriverManagerDataSource(serverUrl, "root", mysql.getPassword())));
    }

    public void begin() {
        root.execute("set global read_only = on");
    }

    public void end() {
        root.execute("set global read_only = off");
    }
}
