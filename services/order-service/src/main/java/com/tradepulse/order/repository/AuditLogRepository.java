package com.tradepulse.order.repository;

import com.tradepulse.order.domain.entity.OrderAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends MongoRepository<OrderAuditLog, String> {

    List<OrderAuditLog> findByOrderIdOrderByTimestampAsc(UUID orderId);

    List<OrderAuditLog> findByUserIdOrderByTimestampDesc(UUID userId);
}
