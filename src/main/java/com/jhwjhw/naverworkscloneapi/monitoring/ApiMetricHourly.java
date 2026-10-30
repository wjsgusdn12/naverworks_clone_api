package com.jhwjhw.naverworkscloneapi.monitoring;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

// api_metric_hourly 테이블 모양(1시간 요약 한 행). 읽고 쓰기는 "더하기" SQL이 필요해
// ApiMetricHourlyRepository가 JdbcTemplate으로 처리하고, 이 클래스는 테이블 구조 확인(validate)용.
@Entity
@Table(name = "api_metric_hourly",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_metric_hourly", columnNames = { "hour_start", "endpoint" }))
@Getter
public class ApiMetricHourly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hour_start", nullable = false)
    private Instant hourStart;

    // '*'면 그 시간 전체 합계
    @Column(nullable = false, length = 300)
    private String endpoint;

    @Column(name = "request_count", nullable = false)
    private long requestCount;
    @Column(name = "client_errors", nullable = false)
    private long clientErrors;
    @Column(name = "server_errors", nullable = false)
    private long serverErrors;
    @Column(name = "duration_sum_ms", nullable = false)
    private long durationSumMs;

    // 응답 시간 구간별 개수(MetricsBucket.LATENCY_BOUNDS_MS와 같은 구간)
    @Column(name = "latency_0", nullable = false)
    private long latency0;
    @Column(name = "latency_1", nullable = false)
    private long latency1;
    @Column(name = "latency_2", nullable = false)
    private long latency2;
    @Column(name = "latency_3", nullable = false)
    private long latency3;
    @Column(name = "latency_4", nullable = false)
    private long latency4;
    @Column(name = "latency_5", nullable = false)
    private long latency5;
    @Column(name = "latency_6", nullable = false)
    private long latency6;
    @Column(name = "latency_7", nullable = false)
    private long latency7;
    @Column(name = "latency_8", nullable = false)
    private long latency8;

    protected ApiMetricHourly() {
    }
}
