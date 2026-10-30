package com.jhwjhw.naverworkscloneapi.monitoring;

import jakarta.persistence.*;
import lombok.Getter;

// api_metric_bucket 테이블 모양(5분 칸 한 행). 읽고 쓰기는 "더하기·덮어쓰기" SQL이 필요해
// ApiMetricBucketRepository(Ring.FIVE_MINUTES)가 JdbcTemplate으로 처리하고, 이 클래스는 테이블 구조 확인(validate)용.
@Entity
@Table(name = "api_metric_bucket",
        uniqueConstraints = @UniqueConstraint(name = "uk_api_metric_bucket", columnNames = { "slot", "endpoint" }),
        indexes = @Index(name = "idx_api_metric_bucket_bucket", columnList = "bucket"))
@Getter
public class ApiMetricBucket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 돌려 쓰는 자리 번호(5분 칸 번호 % 300)
    @Column(nullable = false)
    private int slot;

    // '*'면 그 칸 전체 합계
    @Column(nullable = false, length = 300)
    private String endpoint;

    // 지금 이 자리에 담긴 5분 칸 번호(1970년부터 5분 단위로 센 번호)
    @Column(nullable = false)
    private long bucket;

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

    protected ApiMetricBucket() {
    }
}
