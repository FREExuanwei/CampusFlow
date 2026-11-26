package com.campusflow.modules.activity.service;

import com.campusflow.common.api.ErrorCode;
import com.campusflow.common.api.PageResponse;
import com.campusflow.common.config.DatabaseWebModule;
import com.campusflow.common.exception.BusinessException;
import com.campusflow.modules.activity.dto.ActivityForm;
import com.campusflow.modules.activity.dto.ActivityTransitions;
import com.campusflow.modules.activity.dto.ActivityView;
import com.campusflow.modules.activity.dto.PublishedActivity;
import com.campusflow.modules.activity.dto.PublishedActivityFilter;
import com.campusflow.modules.activity.entity.Activity;
import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.activity.repository.ActivityRepository;
import com.campusflow.modules.enrollment.entity.Enrollment.Status;
import com.campusflow.modules.enrollment.repository.EnrollmentRepository;
import com.campusflow.modules.operations.service.OperationsService;
import com.campusflow.modules.operations.service.OperationsService.Action;
import com.campusflow.modules.operations.service.OperationsService.NoticeType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@DatabaseWebModule
public class ActivityService {

    private static final Instant SQL_TIME_LIMIT = Instant.parse("9999-12-31T23:59:59.999999Z");
    private static final Instant SQL_TIME_START = Instant.parse("1000-01-01T00:00:00Z");
    private final ActivityRepository activities;
    private final EnrollmentRepository enrollments;
    private final OperationsService operations;

    public ActivityService(ActivityRepository activities, EnrollmentRepository enrollments, OperationsService operations) {
        this.activities = activities;
        this.enrollments = enrollments;
        this.operations = operations;
    }

    @Transactional
    public ActivityView create(long actorId, ActivityForm form) {
        if (form.version() != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        validateSchedule(form);
        var activity = activities.saveAndFlush(new Activity(form, actorId));
        operations.record(Action.ACTIVITY_CREATED, actorId, activity.getId());
        return ActivityView.from(activity);
    }

    @Transactional
    public ActivityView edit(long id, long actorId, ActivityForm form) {
        if (form.version() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        validateSchedule(form);
        var activity = requireActivity(id);
        if ((activity.getStatus() != ActivityStatus.DRAFT && activity.getStatus() != ActivityStatus.REJECTED)
                || activity.getVersion() != form.version()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        activity.updateDraft(form);
        return saveChanges(activity, actorId, Action.ACTIVITY_EDITED);
    }

    @Transactional
    public ActivityView submit(long id, long actorId, long version) {
        var activity = requireState(id, version, ActivityStatus.DRAFT);
        requireUpcoming(activity);
        activity.submit();
        return saveChanges(activity, actorId, Action.ACTIVITY_SUBMITTED);
    }

    @Transactional
    public ActivityView review(long id, long actorId, ActivityTransitions.Review request) {
        var activity = requireState(id, request.version(), ActivityStatus.SUBMITTED);
        boolean approved = request.decision() == ActivityTransitions.Decision.APPROVE;
        String reason = request.reason() == null ? null : request.reason().strip();
        if (!approved && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        activity.review(actorId, approved ? ActivityStatus.APPROVED : ActivityStatus.REJECTED, reason, currentTime());
        operations.sendNotification(approved ? NoticeType.ACTIVITY_APPROVED : NoticeType.ACTIVITY_REJECTED,
                activity.getCreatedBy(), id);
        return saveChanges(activity, actorId, approved ? Action.ACTIVITY_APPROVED : Action.ACTIVITY_REJECTED);
    }

    @Transactional
    public ActivityView publish(long id, long actorId, long version) {
        var activity = requireState(id, version, ActivityStatus.APPROVED);
        requireUpcoming(activity);
        activity.publish(currentTime());
        operations.sendNotification(NoticeType.ACTIVITY_PUBLISHED, activity.getCreatedBy(), id);
        return saveChanges(activity, actorId, Action.ACTIVITY_PUBLISHED);
    }

    @Transactional(readOnly = true)
    public ActivityView detail(long id) {
        return ActivityView.from(requireActivity(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityView> list(ActivityStatus status, String title, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from(activities.search(status, title, pageable).map(ActivityView::from));
    }

    @Transactional(readOnly = true)
    public PublishedActivity publishedDetail(long id) {
        var activity = activities.findByIdAndStatus(id, ActivityStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return PublishedActivity.from(activity, enrollments.countByActivityIdAndStatus(id, Status.ENROLLED), currentTime());
    }

    @Transactional(readOnly = true)
    public PageResponse<PublishedActivity> publishedList(PublishedActivityFilter filter, int page, int size) {
        if (outsideSqlRange(filter.startsFrom()) || outsideSqlRange(filter.startsBefore())
                || (filter.startsFrom() != null && filter.startsBefore() != null
                    && !filter.startsFrom().isBefore(filter.startsBefore()))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = currentTime();
        var pageable = PageRequest.of(page, size, Sort.by("startsAt", "id"));
        var results = activities.findAll(matchesPublished(filter, now), pageable);
        Map<Long, Long> occupied = results.isEmpty() ? Map.of() : enrollments
                .countByActivityIdsAndStatus(results.map(Activity::getId).getContent(), Status.ENROLLED)
                .stream().collect(Collectors.toMap(EnrollmentRepository.ActivityCount::getActivityId,
                        EnrollmentRepository.ActivityCount::getTotal));
        return PageResponse.from(results.map(activity ->
                PublishedActivity.from(activity, occupied.getOrDefault(activity.getId(), 0L), now)));
    }

    private static Specification<Activity> matchesPublished(PublishedActivityFilter filter, Instant now) {
        return (activity, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            conditions.add(cb.equal(activity.get("status"), ActivityStatus.PUBLISHED));
            if (filter.title() != null) {
                conditions.add(cb.gt(cb.locate(cb.lower(activity.get("title")),
                        filter.title().toLowerCase(Locale.ROOT)), 0));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String keyword = filter.keyword().strip().toLowerCase(Locale.ROOT);
                conditions.add(cb.or(
                        cb.gt(cb.locate(cb.lower(activity.get("title")), keyword), 0),
                        cb.gt(cb.locate(cb.lower(activity.get("description")), keyword), 0),
                        cb.gt(cb.locate(cb.lower(activity.get("location")), keyword), 0)));
            }
            if (filter.category() != null) {
                conditions.add(cb.equal(activity.get("category"), filter.category()));
            }
            if (filter.startsFrom() != null) {
                conditions.add(cb.greaterThanOrEqualTo(activity.get("startsAt"), filter.startsFrom()));
            }
            if (filter.startsBefore() != null) {
                conditions.add(cb.lessThan(activity.get("startsAt"), filter.startsBefore()));
            }
            if (filter.phase() != null) {
                conditions.add(switch (filter.phase()) {
                    case UPCOMING -> cb.greaterThan(activity.get("startsAt"), now);
                    case ONGOING -> cb.and(cb.lessThanOrEqualTo(activity.get("startsAt"), now),
                            cb.greaterThan(activity.get("endsAt"), now));
                    case ENDED -> cb.lessThanOrEqualTo(activity.get("endsAt"), now);
                });
            }
            return cb.and(conditions);
        };
    }

    private static boolean outsideSqlRange(Instant instant) {
        return instant != null && (instant.isBefore(SQL_TIME_START) || instant.isAfter(SQL_TIME_LIMIT));
    }

    private ActivityView saveChanges(Activity activity, long actorId, Action action) {
        activities.flush();
        operations.record(action, actorId, activity.getId());
        return ActivityView.from(activity);
    }

    private Activity requireState(long id, long version, ActivityStatus expected) {
        var activity = requireActivity(id);
        if (activity.getVersion() != version || activity.getStatus() != expected) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return activity;
    }

    private static void requireUpcoming(Activity activity) {
        if (!activity.getStartsAt().isAfter(currentTime())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private Activity requireActivity(long id) {
        return activities.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private static void validateSchedule(ActivityForm form) {
        var starts = form.startsAt().truncatedTo(ChronoUnit.MICROS);
        var ends = form.endsAt().truncatedTo(ChronoUnit.MICROS);
        if (!starts.isAfter(currentTime()) || !ends.isAfter(starts) || ends.isAfter(SQL_TIME_LIMIT)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static Instant currentTime() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
