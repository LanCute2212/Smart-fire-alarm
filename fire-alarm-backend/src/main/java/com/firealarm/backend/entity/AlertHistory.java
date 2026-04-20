package com.firealarm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.firealarm.backend.entity.enums.SeverityLevel;

@Entity
@Table(name = "alert_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeverityLevel severityLevel;

    @Column(nullable = false, length = 500)
    private String triggerReason;

    @Column(nullable = false)
    private boolean resolved;
}
