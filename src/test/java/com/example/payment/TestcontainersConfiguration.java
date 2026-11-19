package com.example.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		// 2장 기록 실패 테스트가 트리거를 만든다. 바이너리 로그가 켜진 MySQL 8 은 SUPER 권한 없는 사용자의
		// 트리거 생성을 막으므로(오류 1419) 시험용 컨테이너에서만 허용한다
		return new MySQLContainer(DockerImageName.parse("mysql:8.4"))
				.withCommand("--log-bin-trust-function-creators=1");
	}

	// 9장부터: 모든 시험 서버가 시험 JVM 에 하나 띄운 Kafka 브로커(중계를 거쳐)를 쓴다
	@Bean
	DynamicPropertyRegistrar kafkaBroker() {
		return registry -> KafkaBroker.clientProperties().forEach(property -> {
			int split = property.indexOf('=');
			registry.add(property.substring(0, split), () -> property.substring(split + 1));
		});
	}

}
