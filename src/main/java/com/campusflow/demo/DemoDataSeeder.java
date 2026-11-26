package com.campusflow.demo;

import com.campusflow.common.config.DemoRuntime.Account;
import com.campusflow.common.config.DemoRuntime.Credentials;
import com.campusflow.modules.activity.dto.ActivityForm;
import com.campusflow.modules.activity.dto.ActivityTransitions.Decision;
import com.campusflow.modules.activity.dto.ActivityTransitions.Review;
import com.campusflow.modules.activity.dto.ActivityView;
import com.campusflow.modules.activity.enums.ActivityCategory;
import com.campusflow.modules.activity.enums.ActivityStatus;
import com.campusflow.modules.activity.service.ActivityService;
import com.campusflow.modules.enrollment.service.EnrollmentService;
import com.campusflow.modules.user.dto.UserRequests.Register;
import com.campusflow.modules.user.repository.UserAccountRepository;
import com.campusflow.modules.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static com.campusflow.modules.activity.enums.ActivityCategory.ACADEMIC;
import static com.campusflow.modules.activity.enums.ActivityCategory.CULTURE;
import static com.campusflow.modules.activity.enums.ActivityCategory.GENERAL;
import static com.campusflow.modules.activity.enums.ActivityCategory.SPORTS;
import static com.campusflow.modules.activity.enums.ActivityCategory.VOLUNTEER;
import static com.campusflow.modules.activity.enums.ActivityStatus.APPROVED;
import static com.campusflow.modules.activity.enums.ActivityStatus.DRAFT;
import static com.campusflow.modules.activity.enums.ActivityStatus.PUBLISHED;
import static com.campusflow.modules.activity.enums.ActivityStatus.REJECTED;
import static com.campusflow.modules.activity.enums.ActivityStatus.SUBMITTED;

@Component
@Profile("demo")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DemoDataSeeder implements ApplicationRunner {

    private static final ZoneId CAMPUS_TIME = ZoneId.of("Asia/Shanghai");

    private final Credentials credentials;
    private final UserAccountRepository accounts;
    private final UserService users;
    private final ActivityService activities;
    private final EnrollmentService enrollments;
    private final JdbcTemplate jdbc;

    DemoDataSeeder(Credentials credentials, UserAccountRepository accounts, UserService users,
                   ActivityService activities, EnrollmentService enrollments, JdbcTemplate jdbc) {
        this.credentials = credentials;
        this.accounts = accounts;
        this.users = users;
        this.activities = activities;
        this.enrollments = enrollments;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.existsByUsername(credentials.admin().username())) {
            return;
        }

        long admin = register(credentials.admin(), "活动中心值班员");
        long student = register(credentials.student(), "演示同学一号");
        long secondStudent = register(credentials.student2(), "演示同学二号");
        int promoted = jdbc.update("""
                UPDATE user_roles SET role_id = (SELECT id FROM roles WHERE role_code = 'ADMIN')
                WHERE user_id = ? AND role_id = (SELECT id FROM roles WHERE role_code = 'STUDENT')
                """, admin);
        if (promoted != 1) {
            throw new IllegalStateException("Could not assign the demo administrator role.");
        }

        LocalDate today = LocalDate.now(CAMPUS_TIME);
        seed(admin, today, 3, "校园摄影漫步", "从图书馆广场出发，沿湖边步道采集校园的日常片段。"
                        + "手机和相机都欢迎，活动结束前一起挑选作品并交换拍摄心得。",
                "图书馆南门集合", GENERAL, 28, PUBLISHED);
        long music = seed(admin, today, 5, "草坪音乐会", "在傍晚的草坪上听学生乐队带来原创和改编曲目，"
                        + "中场设开放麦。请自带坐垫，散场时带走随身物品。",
                "中央草坪", CULTURE, 80, PUBLISHED);
        long ai = seed(admin, today, 7, "AI 创新工坊", "结对设计一个能解决校园小问题的 AI 原型。"
                        + "活动含演示、分组实践和互评，初学者可以现场跟练。",
                "创新实验室 203", ACADEMIC, 32, PUBLISHED);
        seed(admin, today, 8, "星光五公里校园跑", "沿操场和环湖步道完成五公里夜跑，"
                        + "现场按配速分组，新手可以参与短程组。",
                "田径场东入口", SPORTS, 45, PUBLISHED);
        long books = seed(admin, today, 10, "图书漂流志愿行动", "和图书馆志愿队一起整理捐赠书籍、制作书签，"
                        + "并将图书送往教学楼的共享书架。",
                "图书馆一楼共享阅览区", VOLUNTEER, 12, PUBLISHED);
        seed(admin, today, 11, "蓝晒手作工作坊", "采集落叶和纹理，体验用感光纸记录校园植物。"
                        + "材料在现场领取，完成的蓝晒作品可以带回。",
                "艺术楼 106", CULTURE, 20, PUBLISHED);
        seed(admin, today, 13, "校园观鸟入门", "社团成员带队沿湿地与林荫路观鸟，"
                        + "介绍常见鸟类的识别方法和观测礼仪。",
                "南湖栈道入口", GENERAL, 16, PUBLISHED);
        seed(admin, today, 16, "开源地图共创夜", "一起为校园步行地图补充无障碍通道、休息点和饮水点。"
                        + "带电脑参加，现场提供入门指引。",
                "创客空间 305", ACADEMIC, 24, PUBLISHED);
        seed(admin, today, 18, "旧物修理小站", "学习更换纽扣、修补书包和调整车铃，把小修小补变成可分享的技能。",
                "学生服务中心一楼", VOLUNTEER, 18, PUBLISHED);
        long clinic = seed(admin, today, 20, "飞盘双人训练", "体育社安排两人小组练习短距离传接和基本步法。",
                "第二运动场北侧", SPORTS, 2, PUBLISHED);
        seed(admin, today, 22, "校园纪录片放映交流", "放映学生团队记录校园生活的短片，映后邀请导演与观众交流。",
                "学生活动中心小剧场", CULTURE, 56, PUBLISHED);
        long archive = seed(admin, today, 24, "老照片里的校园", "用档案图片一起寻找教学楼、校道和食堂留下的校园记忆。",
                "校史馆导览厅", GENERAL, 22, PUBLISHED);

        seed(admin, today, 25, "春芽阅读分享会", "带一本愿意推荐的书，准备三分钟介绍。志愿者负责布置交换阅读角。",
                "图书馆二楼研讨室", CULTURE, 18, SUBMITTED);
        seed(admin, today, 27, "校园无障碍实地观察", "按小组记录校内通行体验并整理建议，由志愿服务社汇总。",
                "图书馆东门集合", VOLUNTEER, 18, SUBMITTED);
        seed(admin, today, 28, "期末减压瑜伽", "需要进一步确认活动教室的使用时间和可用器材。",
                "风雨操场活动室", SPORTS, 20, DRAFT);
        seed(admin, today, 29, "传感器入门实验课", "在助教指导下组装校园气象传感器并读取温湿度数据。",
                "创新实验室 201", ACADEMIC, 16, APPROVED);
        seed(admin, today, 30, "插画作品交流集市", "社团将在补齐场地和志愿服务安排后重新提交申请。",
                "艺术中心公共区", CULTURE, 26, REJECTED);

        enrollments.enroll(music, student);
        enrollments.enroll(music, secondStudent);
        enrollments.enroll(ai, secondStudent);
        enrollments.enroll(books, student);
        enrollments.cancel(books, student);
        enrollments.enroll(clinic, student);
        enrollments.enroll(clinic, secondStudent);
        enrollments.enroll(archive, student);
        archiveCompletedActivity(archive, today);
    }

    private long register(Account account, String displayName) {
        return users.register(new Register(account.username(), account.password(), displayName)).id();
    }

    private long seed(long admin, LocalDate today, int ahead, String title, String description, String location,
                      ActivityCategory category, int capacity, ActivityStatus status) {
        Instant starts = today.plusDays(ahead).atTime(LocalTime.of(15, 30)).atZone(CAMPUS_TIME).toInstant();
        ActivityView activity = activities.create(admin, new ActivityForm(title, description, location, starts,
                starts.plus(100, ChronoUnit.MINUTES), capacity, null, category));
        if (status == DRAFT) {
            return activity.id();
        }
        activity = activities.submit(activity.id(), admin, activity.version());
        if (status == SUBMITTED) {
            return activity.id();
        }
        boolean reject = status == REJECTED;
        activity = activities.review(activity.id(), admin, new Review(activity.version(),
                reject ? Decision.REJECT : Decision.APPROVE, reject ? "请补充场地预约凭证和现场分工。" : "场地与活动方案已确认。"));
        if (status == PUBLISHED) {
            return activities.publish(activity.id(), admin, activity.version()).id();
        }
        return activity.id();
    }

    private void archiveCompletedActivity(long id, LocalDate today) {
        // Recreate a completed timeline after passing the normal publication and enrollment checks.
        Instant starts = today.minusDays(3).atTime(15, 30).atZone(CAMPUS_TIME).toInstant();
        LocalDateTime start = LocalDateTime.ofInstant(starts, ZoneOffset.UTC);
        LocalDateTime published = start.minusDays(2);
        jdbc.update("""
                UPDATE activities SET starts_at = ?, ends_at = ?, created_at = ?, updated_at = ?,
                    reviewed_at = ?, published_at = ? WHERE id = ?
                """, start, start.plusMinutes(100), published.minusDays(1), published, published, published, id);
        jdbc.update("UPDATE enrollments SET enrolled_at = ? WHERE activity_id = ?", start.minusDays(1), id);
    }
}
