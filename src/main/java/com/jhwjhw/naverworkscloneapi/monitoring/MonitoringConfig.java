package com.jhwjhw.naverworkscloneapi.monitoring;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @Scheduled(5초마다 집계 전송, 10초마다 서버 상태 보고)가 동작하도록 예약 작업 기능을 켬.
@Configuration
@EnableScheduling
public class MonitoringConfig {
}
