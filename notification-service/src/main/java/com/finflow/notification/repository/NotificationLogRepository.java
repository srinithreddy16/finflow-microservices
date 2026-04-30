package com.finflow.notification.repository;

import com.finflow.notification.model.NotificationLog;
import com.finflow.notification.model.NotificationStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {

    List<NotificationLog> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<NotificationLog> findByStatusAndRetryCountLessThan(
            NotificationStatus status, int maxRetries);

    long countByAccountIdAndEventType(String accountId, String eventType);

    Page<NotificationLog> findByStatusOrderByCreatedAtDesc(
            NotificationStatus status, Pageable pageable);
}
