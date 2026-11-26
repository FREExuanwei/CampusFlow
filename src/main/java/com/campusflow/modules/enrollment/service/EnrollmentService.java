package com.campusflow.modules.enrollment.service;

import com.campusflow.common.api.ErrorCode;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.exception.BusinessException;
import com.campusflow.modules.activity.entity.Activity;
import com.campusflow.modules.activity.enums.ActivityPhase;
import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.activity.repository.ActivityRepository;
import com.campusflow.modules.enrollment.dto.AdminEnrollment;
import com.campusflow.modules.enrollment.dto.EnrollmentView;
import com.campusflow.modules.enrollment.dto.MyEnrollment;
import com.campusflow.modules.enrollment.entity.Enrollment;
import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import com.campusflow.modules.enrollment.repository.EnrollmentRepository;
import com.campusflow.modules.operations.service.OperationsService;
import com.campusflow.modules.operations.service.OperationsService.Action;
import com.campusflow.modules.operations.service.OperationsService.NoticeType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@DatabaseWebModule
public class EnrollmentService {

    private final ActivityRepository activities;
    private final EnrollmentRepository enrollments;
    private final OperationsService operations;

    public EnrollmentService(ActivityRepository activities, EnrollmentRepository enrollments, OperationsService operations) {
        this.activities = activities;
        this.enrollments = enrollments;
        this.operations = operations;
    }

    @Transactional
    public Result enroll(long activityId, long userId) {
        // Every write takes this lock before reading or changing seat allocation.
        Activity activity = lockPublished(activityId);
        var existing = enrollments.findByActivityIdAndUserId(activityId, userId);
        if (existing.isPresent() && existing.get().getStatus() == Status.ENROLLED) {
            return new Result(EnrollmentView.from(existing.get()), false);
        }
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        requireUpcoming(activity, now);
        if (enrollments.countByActivityIdAndStatus(activityId, Status.ENROLLED) >= activity.getCapacity()) {
            throw new BusinessException(ErrorCode.CAPACITY_FULL);
        }
        if (existing.isPresent()) {
            var enrollment = existing.get();
            enrollment.enroll(now);
            confirmation(enrollment, userId);
            return new Result(EnrollmentView.from(enrollment), false);
        }
        var enrollment = enrollments.saveAndFlush(new Enrollment(activityId, userId, now));
        confirmation(enrollment, userId);
        return new Result(EnrollmentView.from(enrollment), true);
    }

    @Transactional
    public EnrollmentView cancel(long activityId, long userId) {
        Activity activity = lockPublished(activityId);
        var enrollment = enrollments.findByActivityIdAndUserId(activityId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (enrollment.getStatus() == Status.ENROLLED) {
            var now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            requireUpcoming(activity, now);
            enrollment.cancel(now);
            operations.record(Action.ENROLLMENT_CANCELLED, userId, enrollment.getId());
            operations.sendNotification(NoticeType.ENROLLMENT_CANCELLED, userId, activityId);
        }
        return EnrollmentView.from(enrollment);
    }

    @Transactional(readOnly = true)
    public PageResponse<MyEnrollment> mine(long userId, Status status, ActivityPhase phase, int page, int size) {
        String phaseFilter = phase == null ? null : phase.name();
        return PageResponse.from(enrollments.findMine(userId, status, phaseFilter,
                Instant.now().truncatedTo(ChronoUnit.MICROS), newestFirst(page, size)));
    }

    @Transactional(readOnly = true)
    public EnrollmentView mineByActivity(long activityId, long userId) {
        return enrollments.findByActivityIdAndUserId(activityId, userId)
                .map(EnrollmentView::from).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MyEnrollment mineById(long userId, long id) {
        return enrollments.findMineById(userId, id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminEnrollment> roster(long activityId, Status status, int page, int size) {
        requireActivityExists(activityId);
        return PageResponse.from(enrollments.findRoster(activityId, status, newestFirst(page, size)));
    }

    @Transactional(readOnly = true)
    public RosterSummary rosterSummary(long activityId) {
        requireActivityExists(activityId);
        long active = 0;
        long cancelled = 0;
        for (var row : enrollments.countByStatus(activityId)) {
            switch (row.getStatus()) {
                case ENROLLED -> active = row.getTotal();
                case CANCELLED -> cancelled = row.getTotal();
            }
        }
        return new RosterSummary(active + cancelled, active, cancelled);
    }

    private void requireActivityExists(long activityId) {
        if (!activities.existsById(activityId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private static PageRequest newestFirst(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
    }

    private Activity lockPublished(long id) {
        return activities.findLockedById(id).filter(activity -> activity.getStatus() == ActivityStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private void confirmation(Enrollment enrollment, long userId) {
        operations.record(Action.ENROLLMENT_CREATED, userId, enrollment.getId());
        operations.sendNotification(NoticeType.ENROLLMENT_CONFIRMED, userId, enrollment.getActivityId());
    }

    private static void requireUpcoming(Activity activity, Instant now) {
        if (!activity.getStartsAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    public record Result(EnrollmentView enrollment, boolean created) {}
    public record RosterSummary(long total, long active, long cancelled) {}
}
