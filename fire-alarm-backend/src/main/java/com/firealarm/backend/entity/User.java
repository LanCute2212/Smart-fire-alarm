package com.firealarm.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.util.List;
import com.firealarm.backend.entity.enums.Role;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String fcmToken;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    private List<Device> devices;
}
