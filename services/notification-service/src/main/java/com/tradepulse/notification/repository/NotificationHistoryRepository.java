package com.tradepulse.notification.repository;

import com.tradepulse.notification.domain.entity.NotificationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.UUID;

public interface NotificationHistoryRepository extends MongoRepository<NotificationHistory, String> {

    Page<NotificationHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
