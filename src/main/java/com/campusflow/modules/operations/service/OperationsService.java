package com.campusflow.modules.operations.service;

import com.campusflow.common.api.ErrorCode;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.exception.BusinessException;
import com.campusflow.modules.activity.enums.ActivityStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@DatabaseWebModule
public class OperationsService {

    public enum Action {
        ACTIVITY_CREATED("ACTIVITY"), ACTIVITY_EDITED("ACTIVITY"), ACTIVITY_SUBMITTED("ACTIVITY"),
        ACTIVITY_APPROVED("ACTIVITY"), ACTIVITY_REJECTED("ACTIVITY"), ACTIVITY_PUBLISHED("ACTIVITY"),
        ENROLLMENT_CREATED("ENROLLMENT"), ENROLLMENT_CANCELLED("ENROLLMENT"), USER_STATUS_CHANGED("USER");

        private final String targetType;

        Action(String targetType) {
            this.targetType = targetType;
        }
    }

    public enum NoticeType {
        ACTIVITY_APPROVED("活动审核已通过"), ACTIVITY_REJECTED("活动审核已驳回"),
        ACTIVITY_PUBLISHED("活动已发布"), ENROLLMENT_CONFIRMED("报名已确认"),
        ENROLLMENT_CANCELLED("报名已取消");

        private final String message;

        NoticeType(String message) {
            this.message = message;
        }
    }

    public record Notification(long id, long activityId, NoticeType type, String message,
                               Instant createdAt, Instant readAt) {}

    public record Operation(long id, long actorId, Action action, String targetType, long targetId,
                            Instant createdAt) {}

    public record Statistics(long users, long activities, long publishedActivities,
                             long activeEnrollments, long cancelledEnrollments, long pendingActivities,
                             long totalEnrollments, Map<ActivityStatus, Long> activityStatusCounts) {}

    private static final RowMapper<Notification> NOTICE = (rs, row) -> {
        NoticeType type = NoticeType.valueOf(rs.getString("type"));
        return new Notification(rs.getLong("id"), rs.getLong("activity_id"), type, type.message,
                timestamp(rs, "created_at"), timestamp(rs, "read_at"));
    };

    private final JdbcTemplate jdbc;

    public OperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Action action, long actorId, long targetId) {
        jdbc.update("""
                INSERT INTO operation_logs (actor_id, action, target_type, target_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, actorId, action.name(), action.targetType, targetId, now());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sendNotification(NoticeType type, long userId, long activityId) {
        jdbc.update("""
                INSERT INTO notifications (user_id, activity_id, type, created_at)
                VALUES (?, ?, ?, ?)
                """, userId, activityId, type.name(), now());
    }

    @Transactional(readOnly = true)
    public PageResponse<Notification> inbox(long userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var rows = jdbc.query("""
                SELECT id, activity_id, type, created_at, read_at FROM notifications
                WHERE user_id = ? AND (? = FALSE OR read_at IS NULL)
                ORDER BY id DESC LIMIT ? OFFSET ?
                """, NOTICE, userId, unreadOnly, size, pageable.getOffset());
        long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notifications WHERE user_id = ? AND (? = FALSE OR read_at IS NULL)
                """, Long.class, userId, unreadOnly);
        return PageResponse.from(new PageImpl<>(rows, pageable, count));
    }

    @Transactional
    public Notification markRead(long userId, long id) {
        jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND id = ? AND read_at IS NULL",
                now(), userId, id);
        return notification(userId, id);
    }

    @Transactional(readOnly = true)
    public Notification notification(long userId, long id) {
        return jdbc.query("""
                SELECT id, activity_id, type, created_at, read_at FROM notifications WHERE user_id = ? AND id = ?
                """, NOTICE, userId, id).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
    }

    @Transactional
    public int markAllRead(long userId) {
        return jdbc.update("UPDATE notifications SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                now(), userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<Operation> audit(Action action, int page, int size) {
        var pageable = PageRequest.of(page, size);
        String filter = action == null ? "" : action.name();
        var rows = jdbc.query("""
                SELECT id, actor_id, action, target_type, target_id, created_at FROM operation_logs
                WHERE (? = '' OR action = ?) ORDER BY id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new Operation(rs.getLong("id"), rs.getLong("actor_id"),
                        Action.valueOf(rs.getString("action")), rs.getString("target_type"),
                        rs.getLong("target_id"), timestamp(rs, "created_at")),
                filter, filter, size, pageable.getOffset());
        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_logs WHERE (? = '' OR action = ?)", Long.class, filter, filter);
        return PageResponse.from(new PageImpl<>(rows, pageable, count));
    }

    @Transactional(readOnly = true)
    public Statistics statistics() {
        return jdbc.queryForObject("""
                SELECT (SELECT COUNT(*) FROM users) AS user_count, activity_counts.*, enrollment_counts.*
                FROM (
                    SELECT COUNT(*) AS activity_count,
                           COUNT(CASE WHEN status = 'DRAFT' THEN 1 END) AS draft_count,
                           COUNT(CASE WHEN status = 'SUBMITTED' THEN 1 END) AS pending_count,
                           COUNT(CASE WHEN status = 'APPROVED' THEN 1 END) AS approved_count,
                           COUNT(CASE WHEN status = 'REJECTED' THEN 1 END) AS rejected_count,
                           COUNT(CASE WHEN status = 'PUBLISHED' THEN 1 END) AS published_count
                    FROM activities
                ) activity_counts CROSS JOIN (
                    SELECT COUNT(*) AS enrollment_count,
                           COUNT(CASE WHEN status = 'ENROLLED' THEN 1 END) AS enrolled_count,
                           COUNT(CASE WHEN status = 'CANCELLED' THEN 1 END) AS cancelled_count
                    FROM enrollments
                ) enrollment_counts
                """, (rs, row) -> {
                    long published = rs.getLong("published_count");
                    long pending = rs.getLong("pending_count");
                    return new Statistics(rs.getLong("user_count"), rs.getLong("activity_count"), published,
                            rs.getLong("enrolled_count"), rs.getLong("cancelled_count"), pending,
                            rs.getLong("enrollment_count"), Map.of(
                                    ActivityStatus.DRAFT, rs.getLong("draft_count"), ActivityStatus.SUBMITTED, pending,
                                    ActivityStatus.APPROVED, rs.getLong("approved_count"),
                                    ActivityStatus.REJECTED, rs.getLong("rejected_count"),
                                    ActivityStatus.PUBLISHED, published));
                });
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static Instant timestamp(ResultSet rs, String field) throws SQLException {
        LocalDateTime value = rs.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
