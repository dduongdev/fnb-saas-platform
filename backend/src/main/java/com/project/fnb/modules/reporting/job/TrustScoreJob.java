package com.project.fnb.modules.reporting.job;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Slf4j
public class TrustScoreJob {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Chạy lúc 01:00 AM mỗi ngày.
     * Logic: Quét bảng SHIFTS của ngày hôm qua (YESTERDAY).
     * Nếu Employee có ca COMPLETED -> +1 điểm Trust Score cho User tương ứng.
     */
    @Scheduled(cron = "0 0 1 * * ?") // 1 giờ sáng
    @Transactional
    public void updateUserTrustScores() {
        log.info("⏰ Starting Trust Score Update Job...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        String sql = """
            UPDATE users u 
            SET u.trust_score = u.trust_score + 1 
            WHERE u.id IN (
                SELECT DISTINCT e.user_id 
                FROM employees e 
                JOIN shifts s ON s.employee_id = e.id 
                WHERE DATE(s.start_time) = :yesterday 
                AND s.status = 'COMPLETED'
                AND s.is_deleted = false
            )
        """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("yesterday", yesterday);

        int updatedCount = query.executeUpdate();
        
        log.info("✅ Trust Score Job Finished. Updated scores for {} users based on work activity on {}", updatedCount, yesterday);
    }
}