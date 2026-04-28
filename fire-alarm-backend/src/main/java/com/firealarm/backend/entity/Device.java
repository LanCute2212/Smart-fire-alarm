package com.firealarm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import com.firealarm.backend.entity.enums.DeviceStatus;

@Entity
@Table(name = "devices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    private String deviceId; // e.g. MAC address

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceStatus status;

    @Column(nullable = false, unique = true)
    private String macAddress;

    /** Mã bí mật 1 lần để khách hàng claim thiết bị */
    @Column(unique = true)
    private String claimToken;

    /** Vị trí lắp đặt (phòng khách, bếp, v.v.) */
    private String location;

    /** Tọa độ tương đối trên Floor Map */
    private Float mapX;
    private Float mapY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;
}
