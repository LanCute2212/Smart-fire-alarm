package com.firealarm.backend.repository;

import com.firealarm.backend.entity.AlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, UUID> {
    List<AlertHistory> findByDevice_DeviceIdOrderByTimestampDesc(String deviceId);
}
