package com.example.demo.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventApplicationRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.EventUnpublishRequestRepo;
import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.RequestLogRepo;
import com.example.demo.Repository.StatusLogRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminEventDetailProjection;
import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.AdminNoticeProjection;
import com.example.demo.Repository.projection.admin.AdminOrgEventLogProjection;
import com.example.demo.Repository.projection.admin.AdminOrganizerDetailProjection;
import com.example.demo.Repository.projection.admin.AdminVenderDetailProjection;
import com.example.demo.Repository.projection.admin.ApplicationDateProjection;
import com.example.demo.Repository.projection.admin.EventApprovalProjection;
import com.example.demo.Repository.projection.admin.EventStatusLogProjection;
import com.example.demo.Repository.projection.admin.EventUnpublishReasonProjection;
import com.example.demo.Repository.projection.admin.EventUnpublishReviewProjection;
import com.example.demo.Repository.projection.admin.RefundProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.dto.response.admin.BatchUserStatusResultDto;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.demo.Repository.projection.admin.UserLoginLogProjection;
import com.example.demo.Repository.projection.admin.VenderRegApplicationProjection;
import com.example.demo.Repository.specification.AdminLogSpecification;
import com.example.demo.Repository.specification.EventSpecification;
import com.example.demo.Repository.specification.UserSpecification;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.CategoryResponse;
import com.example.demo.dto.response.admin.AdminEventListDto;
import com.example.demo.dto.response.admin.AdminNoticeDto;
import com.example.demo.dto.notification.NotificationContentSanitizer;
import com.example.demo.dto.response.admin.AdminOperationLogDto;
import com.example.demo.dto.response.admin.AdminOrgDetailDto;
import com.example.demo.dto.response.admin.AdminOrgEventManagementDto;
import com.example.demo.dto.response.admin.AdminUserListDto;
import com.example.demo.dto.response.admin.AdminUserLoginDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;
import com.example.demo.dto.response.admin.AdminVenderRegDto;
import com.example.demo.dto.response.admin.BoothZone;
import com.example.demo.dto.response.admin.EventStatusChangeDto;
import com.example.demo.dto.response.admin.RegBooth;
import com.example.demo.dto.response.admin.StatusLog;
import com.example.demo.dto.response.admin.UserStatusChangeDto;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.EventStall;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.PaymentStatus;
import com.example.demo.enums.status.RefundStatus;
import com.example.demo.enums.status.ReviewStatus;
import com.example.demo.enums.status.UnpublishRequestStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.AdminTargetTypeForFront;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
import com.example.demo.enums.type.Role;

import jakarta.annotation.Nonnull;
import jakarta.persistence.Tuple;

@Service
public class AdminService extends AdminServiceBase implements EventStatusServiceInterface<Object> {
    private static final int BATCH_REASON_MAX_LENGTH = 100;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventStallZoneRepo eventStallZoneRepo;

    @Autowired
    EventStallRepo eventStallRepo;

    @Autowired
    UserRepo userRepo;

    @Autowired
    AdminLogRepo logRepo;

    @Autowired
    EventApplicationRepo eventApplicationRepo;

    @Autowired
    RequestLogRepo requestLogRepo;

    @Autowired
    StatusLogRepo statusLogRepo;

    @Autowired
    NotificationRepo notificationRepo;

    @Autowired
    EventUnpublishRequestRepo eventUnpublishRequestRepo;

    @Autowired
    MessageSource messageSource;

    @Autowired
    AdminExceptionNotificationService adminExceptionNotificationService;

    /** yyyy/MM/dd HH:mm */
    private final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    /** yyyy/MM/dd */
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** HH:mm */
    private final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /** 主辦方登入API路徑 */
    private static final List<String> ORGANIZER_LOGIN_PATHS = List.of(
            "/api/organizer/google-login",
            "/api/organizer/local-login");
    /** 攤主登入API路徑 */
    private static final List<String> VENDOR_LOGIN_PATHS = List.of(
            "/api/vendor/google-login",
            "/api/vendor/local-login");

    /** 首頁通知列表預覽筆數 */
    private static final int DASHBOARD_NOTICE_COUNT = 6;

    // 設定管理員後台: 首頁資料統計部分
    @Override
    public AdminDashboardDto getDashboardResponse(String operatorEmail) {
        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        LocalDateTime now = LocalDateTime.now();
        long systemWarningCount = notificationRepo.countUnreadNoticesByCategory(admin.id(), NotificationCategory.EXCEPTION);
        List<AdminNoticeDto> notices = getNotice(null, null, 1, DASHBOARD_NOTICE_COUNT, operatorEmail).getItems();

        // 塞資料
        return new AdminDashboardDto(
                eventRepo.countByWorkflowStatus(WorkflowStatus.PENDING_REVIEW),
                eventRepo.countByWorkflowStatus(WorkflowStatus.MAP_BUILDING),
                eventRepo.countByWorkflowStatus(WorkflowStatus.UNPUBLISH_REQUESTED),
                (int) systemWarningCount,
                userRepo.countByRoleAndStatus(Role.ORGANIZER, UserStatus.ACTIVE),
                userRepo.countByRoleAndStatus(Role.VENDOR, UserStatus.ACTIVE),
                eventRepo.countByEventInPlatform(now),
                eventRepo.countByEventStatusIsACTIVE(now),
                notices);
    }

    /**
     * 設定管理員後台: 通知中心，查詢指定管理員的通知列表，依未讀優先、時間新到舊排序<br>
     * @param category 通知分類，為 null 時查詢全部分類
     * @param isOnlyUnread 為 true 時查詢全部分類且僅未讀通知，為 false/null 時不篩選已讀狀態
     * @param pageNumber 頁碼，從1開始計算
     * @param pageSize 每頁筆數
     * @param operatorEmail 操作者(管理員)email
     * @return 通知列表分頁結果
     * @throws IllegalArgumentException 找不到指定的管理員時拋出
     */
    @Override
    public PageResponse<AdminNoticeDto> getNotice(
            NotificationCategory category, Boolean isOnlyUnread, int pageNumber, int pageSize, String operatorEmail) {
        AdminLookupProjection admin = userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));

        boolean onlyUnread = Boolean.TRUE.equals(isOnlyUnread);
        NotificationCategory effectiveCategory = onlyUnread ? null : category;
        Boolean isRead = onlyUnread ? Boolean.FALSE : null;

        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<AdminNoticeProjection> notices = notificationRepo.findAdminNotices(admin.id(), effectiveCategory, isRead, pageRequest);
        long total = notificationRepo.countAdminNotices(admin.id(), effectiveCategory, isRead);

        List<AdminNoticeDto> items = notices.stream()
                .map(this::toAdminNoticeDto)
                .toList();

        return new PageResponse<>(items, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 活動搜尋
    @Override
    public PageResponse<AdminEventListDto> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize) {
        // pageNumber/pageSize 若為 0 或負數（例如前端未帶值），需與 PageResponse 的正規化邏輯一致，避免查詢時 LIMIT 0 撈成空結果
        pageNumber = PageResponse.normalizePage(pageNumber);
        pageSize = PageResponse.normalizePageSize(pageSize);

        // ----------只撈頁面需要用到的欄位，避免撈出整張表----------
        Specification<MarketEvent> spec = EventSpecification.build(request);
        List<Tuple> rows = eventRepo.findEventListTuples(spec, pageNumber, pageSize);

        // 另外查詢符合條件的總筆數
        long total = eventRepo.count(spec);

        // ----------設定回傳資料----------
        List<AdminEventListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            LocalDateTime startAt = row.get("startAt", LocalDateTime.class);
            LocalDateTime endAt = row.get("endAt", LocalDateTime.class);
            String eventDate = String.format(
                    "%s - %s",
                    startAt == null ? "" : startAt.format(DATE_FORMATTER),
                    endAt == null ? "" : endAt.format(DATE_FORMATTER));
            LocalDateTime submittedAt = row.get("submittedAt", LocalDateTime.class);
            String submittedAtStr = submittedAt == null ? "活動尚未送審" : submittedAt.format(DATE_TIME_FORMATTER);

            WorkflowStatus workflowStatus = row.get("workflowStatus", WorkflowStatus.class);
            Integer maxBooths = row.get("maxBooths", Integer.class);
            Long registeredBoothCount = row.get("registeredBoothCount", Long.class);
            EventStatus status = workflowStatus == null
                    ? null
                    : checkEventStatus(
                            workflowStatus,
                            row.get("registrationStartAt", LocalDateTime.class),
                            row.get("registrationEndAt", LocalDateTime.class),
                            row.get("brandPublicAt", LocalDateTime.class),
                            startAt,
                            endAt,
                            maxBooths == null ? 0 : maxBooths,
                            registeredBoothCount == null ? 0 : registeredBoothCount.intValue());

            AdminEventListDto dtoItem = new AdminEventListDto(
                    row.get("id", Long.class),
                    row.get("coverImageUrl", String.class),
                    row.get("title", String.class),
                    eventDate,
                    status,
                    row.get("organizerName", String.class),
                    submittedAtStr);

            dtoList.add(dtoItem);
        }

        PageResponse<AdminEventListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // 設定管理員後台: 活動詳細
    @Override
    public AdminEventDetailDto getEventDetail(@NonNull Long eventId, int pageSize) throws IllegalArgumentException {
        // ----------撈資料----------
        AdminEventDetailProjection event = eventRepo.findEventDetailById(eventId)  //不會撈到草稿和取消狀態的活動
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        List<EventStallZone> zones = eventStallZoneRepo.findByMarketEventId(eventId);
        List<CategoryResponse> categories = Optional.ofNullable(eventRepo.findCategoriesByEventId(eventId))
                .orElseGet(List::of)
                .stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getSlug()))
                .toList();
        int registeredBoothCount = eventApplicationRepo.countRegBoothsByEventId(eventId);
        // 活動狀態=UNPUBLISH_REQUESTED時，多查一筆待審核下架申請的id與原因
        EventUnpublishReasonProjection unpublishReason = event.workflowStatus() != WorkflowStatus.UNPUBLISH_REQUESTED
                ? null
                : eventUnpublishRequestRepo
                        .findLatestReasonByEventIdAndStatus(eventId, UnpublishRequestStatus.PENDING)
                        .orElse(null);

        // ----------塞資料----------
        String eventTime = String.format(
                "%s - %s %s-%s",
                event.startAt().format(DATE_FORMATTER),
                event.endAt().format(DATE_FORMATTER),
                event.startAt().format(TIME_FORMATTER),
                event.endAt().format(TIME_FORMATTER));
        // 沒有主辦方資料(organizerProfile)時不組營業時間
        String serviceHours = String.format(
                "%s %s-%s",
                event.serviceDays() == null ? "" : event.serviceDays(),
                event.serviceStartTime() == null ? "營業開始時間" : event.serviceStartTime().format(TIME_FORMATTER),
                event.serviceEndTime() == null ? "營業結束時間" : event.serviceEndTime().format(TIME_FORMATTER));
        String addr = String.format(
                "%s%s%s",
                event.city(),
                event.district() == null ? "" : event.district(),
                event.address());
        String contactAddr = String.format(
                "%s%s%s",
                event.contactCity() == null ? "" : event.contactCity(),
                event.contactDist() == null ? "" : event.contactDist(),
                event.contactAddr() == null ? "" : event.contactAddr());

        String boothSpec = String.format(
                "%s * %s",
                event.stallLength() == null ? "0" : event.stallLength().stripTrailingZeros().toPlainString(),
                event.stallWidth() == null ? "0" : event.stallWidth().stripTrailingZeros().toPlainString());
        EventStatus eventStatus = checkEventStatus(
                event.workflowStatus(),
                event.regStartAt(),
                event.regEndAt(),
                event.brandPublicAt(),
                event.startAt(),
                event.endAt(),
                event.maxBooths(),
                registeredBoothCount);

        List<BoothZone> boothZones = zones.stream()
                .map(zone -> new BoothZone(zone.getZoneName(), zone.getStallCount()))
                .toList();

        return new AdminEventDetailDto(
                event.eventId(),
                event.coverImg(),
                event.eventName(),
                event.locationName(),
                addr,
                eventStatus,
                categories,
                event.description(),
                event.regStartAt().format(DATE_TIME_FORMATTER),
                event.regEndAt().format(DATE_TIME_FORMATTER),
                event.publicInfoAt() == null ? null : event.publicInfoAt().format(DATE_TIME_FORMATTER),
                eventTime,
                event.organizerName(),
                event.texId(),
                serviceHours,
                event.contactName(),
                event.contactPhone(),
                event.contactEmail(),
                contactAddr,
                event.mrt(),
                event.bus(),
                event.driving(),
                boothSpec,
                event.maxBooths(),
                event.boothFee(),
                boothZones,
                event.mapImg(),
                unpublishReason == null ? null : unpublishReason.id(),
                unpublishReason == null ? null : unpublishReason.reason(),
                unpublishReason == null ? null : unpublishReason.requestedAt().format(DATE_TIME_FORMATTER),
                getEventStatusLogs(eventId, 1, pageSize));
    }

    // 設定管理員後台: 活動詳細:活動狀態變動紀錄
    @Override
    public PageResponse<StatusLog> getEventStatusLogs(@NonNull Long eventId, int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<EventStatusLogProjection> logs = statusLogRepo.findEventStatusLogs(eventId, pageRequest);
        long total = statusLogRepo.countEventStatusLogs(eventId);

        List<StatusLog> items = logs.stream()
                .map(this::toStatusLog)
                .toList();

        return new PageResponse<StatusLog>(items, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 使用者搜尋
    @Override
    public PageResponse<AdminUserListDto> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        Specification<User> spec = UserSpecification.build(request);
        List<Tuple> rows = userRepo.findUserListTuples(spec, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = userRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminUserListDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            // null處理
            LocalDateTime loginTime = row.get("loginTime", LocalDateTime.class);
            String userName = row.get("name", String.class) == null ? "使用者尚未填寫" : row.get("name", String.class);
            String loginTimeStr = loginTime == null ? null : loginTime.format(DATE_TIME_FORMATTER);

            AdminUserListDto dtoItem = new AdminUserListDto(
                    row.get("id", Long.class),
                    row.get("role", Role.class).getRole(),
                    userName,
                    row.get("status", UserStatus.class).getStatus(),
                    row.get("email", String.class),
                    row.get("regAt", LocalDateTime.class).format(DATE_TIME_FORMATTER),
                    loginTimeStr);

            dtoList.add(dtoItem);
        }
        PageResponse<AdminUserListDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    // for 管理員後台使用者詳細
    @Override
    public AdminVenderDetailDto getVenderDetail(@NonNull Long userId, int pageSize) {
        // ----------撈資料----------
        AdminVenderDetailProjection profile = userRepo.findVenderDetailById(userId) //只能撈到攤主
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的攤主"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastLoginAt = requestLogRepo.findLastLoginAt(userId, VENDOR_LOGIN_PATHS);
        int ongoingEventCount = eventApplicationRepo.countOngoingEvents(userId, now);
        int endedEventCount = eventApplicationRepo.countEndedEvents(userId, now);

        PageResponse<AdminVenderRegDto> eventRegLogs = getVenderRegLogs(userId, 1, pageSize);
        PageResponse<AdminUserLoginDto> loginLogs = getUserLoginLogs(userId, 1, pageSize);

        // ----------塞資料----------
        String userName = profile.userName() == null ? "使用者尚未填寫" : profile.userName();
        boolean isGoogleBound = profile.provider() != User.Provider.LOCAL;
        String contactAddress = String.format(
                "%s%s%s",
                profile.city() == null ? "" : profile.city(),
                profile.district() == null ? "" : profile.district(),
                profile.address() == null ? "" : profile.address());

        return new AdminVenderDetailDto(
                profile.userId(),
                userName,
                profile.role().getRole(),
                profile.accountStatus().getStatus(),
                isGoogleBound,
                profile.regAt().format(DATE_TIME_FORMATTER),
                lastLoginAt == null ? null : lastLoginAt.format(DATE_TIME_FORMATTER),
                ongoingEventCount,
                endedEventCount,
                profile.brandName(),
                profile.brandType(),
                userName,
                profile.contactPhone(),
                profile.contactEmail(),
                contactAddress,
                eventRegLogs,
                loginLogs);
    }

    // 設定管理員後台: 攤主詳細: 活動報名紀錄
    @Override
    public PageResponse<AdminVenderRegDto> getVenderRegLogs(@Nonnull Long userId, int pageNumber, int pageSize) {
        //----------驗證userId.role----------
        if(userRepo.countByIdAndRole(userId, Role.VENDOR) != 1){
            throw new IllegalArgumentException("該使用者非攤主");
        };
        // ----------撈資料----------
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<VenderRegApplicationProjection> applications = eventApplicationRepo.findVenderRegApplications(userId,
                pageRequest);
        long total = eventApplicationRepo.countByUserId(userId);

        List<Long> applicationIds = applications.stream()
                .map(VenderRegApplicationProjection::applicationId)
                .toList();

        // 依報名編號查詢報名攤位(參與日期+已選定攤位)與退款紀錄
        List<ApplicationDateProjection> dates = eventApplicationRepo.findApplicationDates(applicationIds);
        List<RefundProjection> refunds = eventApplicationRepo.findRefunds(applicationIds);

        // ----------依報名編號分組: 報名攤位----------
        Map<Long, List<RegBooth>> regBoothsByApplication = new HashMap<>();
        for (ApplicationDateProjection date : dates) {
            String regDate = date.applyDate() == null ? "" : date.applyDate().format(DATE_FORMATTER);
            String boothNo = (date.stallNo() == null || date.zoneName() == null)
                    ? "尚未選攤位"
                    : date.zoneName() + date.stallNo();

            regBoothsByApplication
                    .computeIfAbsent(date.applicationId(), key -> new ArrayList<>())
                    .add(new RegBooth(regDate, boothNo));
        }

        // ----------依報名編號分組: 是否已有退款(以及是否已完成退款)----------
        Map<Long, Boolean> hasRefundByApplication = new HashMap<>();
        Map<Long, Boolean> hasRefundedAtByApplication = new HashMap<>();
        for (RefundProjection refund : refunds) {
            hasRefundByApplication.put(refund.applicationId(), true);
            if (refund.refundedAt() != null) {
                hasRefundedAtByApplication.put(refund.applicationId(), true);
            }
        }

        // ----------設定回傳資料----------
        List<AdminVenderRegDto> dtoList = new ArrayList<>();
        for (VenderRegApplicationProjection application : applications) {
            String regStatus = resolveRegStatus(
                    application.isCancelled(), application.reviewStatus(), application.paymentStatus());
            String paymentStatusStr = resolvePaymentStatus(
                    application.applicationId(), application.paymentStatus(),
                    hasRefundByApplication, hasRefundedAtByApplication);
            List<RegBooth> regBooths = regBoothsByApplication.getOrDefault(application.applicationId(), List.of());

            dtoList.add(new AdminVenderRegDto(application.eventId(), application.eventName(), regStatus, paymentStatusStr, regBooths));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 主辦方詳細
    @Override
    public AdminOrgDetailDto getOrganizerDetail(Long userId, int pageSize) {
        // ----------撈資料----------
        AdminOrganizerDetailProjection profile = userRepo.findOrganizerDetailById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的主辦方"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastLoginAt = requestLogRepo.findLastLoginAt(userId, ORGANIZER_LOGIN_PATHS);
        int createdEventCount = eventRepo.countCreatedEventsByUserId(userId);
        int ongoingEventCount = eventRepo.countOngoingEventsByUserId(userId, now);
        int endedEventCount = eventRepo.countEndedEventsByUserId(userId, now);

        PageResponse<AdminOrgEventManagementDto> eventLogs = getOrgEventLogs(userId, 1, pageSize);
        PageResponse<AdminUserLoginDto> loginLogs = getUserLoginLogs(userId, 1, pageSize);

        // ----------塞資料----------
        String userName = profile.userName() == null ? "使用者尚未填寫" : profile.userName();
        boolean isGoogleBound = profile.provider() != User.Provider.LOCAL;
        String serviceHours = String.format(
                "%s %s-%s",
                profile.serviceDays() == null ? "" : profile.serviceDays(),
                profile.serviceStartTime() == null ? "營業開始時間" : profile.serviceStartTime().format(TIME_FORMATTER),
                profile.serviceEndTime() == null ? "營業結束時間" : profile.serviceEndTime().format(TIME_FORMATTER));
        String contactAddress = String.format(
                "%s%s%s",
                profile.city() == null ? "" : profile.city(),
                profile.district() == null ? "" : profile.district(),
                profile.address() == null ? "" : profile.address());

        return new AdminOrgDetailDto(
                profile.userId(),
                userName,
                profile.role().getRole(),
                profile.accountStatus().getStatus(),
                isGoogleBound,
                profile.regAt().format(DATE_TIME_FORMATTER),
                lastLoginAt == null ? null : lastLoginAt.format(DATE_TIME_FORMATTER),
                createdEventCount,
                ongoingEventCount,
                endedEventCount,
                profile.organizerName(),
                serviceHours,
                profile.companyName(),
                userName,
                profile.contactPhone(),
                profile.contactEmail(),
                contactAddress,
                profile.taxId(),
                eventLogs,
                loginLogs);
    }

    // 設定管理員後台: 主辦方詳細: 活動管理紀錄
    @Override
    public PageResponse<AdminOrgEventManagementDto> getOrgEventLogs(@Nonnull Long userId, int pageNumber,
            int pageSize) {
        //----------驗證userId.role----------
        if(userRepo.countByIdAndRole(userId, Role.ORGANIZER) != 1){
            throw new IllegalArgumentException("該使用者非主辦方");
        };

        // ----------撈資料----------
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<AdminOrgEventLogProjection> events = eventRepo.findOrgEventLogs(userId, pageRequest);
        long total = eventRepo.countByUserId(userId);

        // ----------設定回傳資料----------
        List<AdminOrgEventManagementDto> dtoList = new ArrayList<>();
        for (AdminOrgEventLogProjection event : events) {
            LocalDateTime startAt = event.startAt();
            LocalDateTime endAt = event.endAt();
            String eventDate = String.format(
                    "%s - %s %s-%s",
                    startAt.format(DATE_FORMATTER),
                    endAt.format(DATE_FORMATTER),
                    startAt.format(TIME_FORMATTER),
                    endAt.format(TIME_FORMATTER));

            int maxBooths = event.maxBooths() == null ? 0 : event.maxBooths();
            int registeredBoothCount = eventApplicationRepo.countRegBoothsByEventId(event.eventId());
            String registrationCount = String.format("%d/%d", registeredBoothCount, maxBooths);

            WorkflowStatus workflowStatus = event.workflowStatus();
            EventStatus status = workflowStatus == null
                    ? null
                    : checkEventStatus(
                            workflowStatus,
                            event.registrationStartAt(),
                            event.registrationEndAt(),
                            event.brandPublicAt(),
                            startAt,
                            endAt,
                            maxBooths,
                            registeredBoothCount);
            String eventStatus = status != null
                    ? status.getStatus()
                    : (workflowStatus == null ? "" : workflowStatus.getDescription());

            String eventName = event.title() == null ? "" : event.title();

            dtoList.add(new AdminOrgEventManagementDto(event.eventId(), eventName, eventDate, eventStatus, registrationCount));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 使用者詳細 :使用者登入紀錄
    @Override
    public PageResponse<AdminUserLoginDto> getUserLoginLogs(Long userId, int pageNumber, int pageSize)
            throws IllegalArgumentException {
        // ----------撈資料----------
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的使用者"));

        // 管理員沒有登入紀錄頁面
        if (user.getRole() == Role.ADMIN) {
            return null;
        }

        List<String> loginPaths = user.getRole() == Role.ORGANIZER ? ORGANIZER_LOGIN_PATHS : VENDOR_LOGIN_PATHS;

        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize);
        List<UserLoginLogProjection> logs = requestLogRepo.findUserLoginLogs(userId, loginPaths, pageRequest);
        long total = requestLogRepo.countUserLoginLogs(userId, loginPaths);

        // ----------設定回傳資料----------
        List<AdminUserLoginDto> dtoList = new ArrayList<>();
        for (UserLoginLogProjection log : logs) {
            String loginMethod = log.path().contains("google") ? "google" : "Email";
            String loginStatus = log.statusCode() != null && log.statusCode() == 200 ? "成功" : "失敗";

            dtoList.add(new AdminUserLoginDto(
                    log.loginTime() == null ? null : log.loginTime().format(DATE_TIME_FORMATTER),
                    loginMethod,
                    loginStatus));
        }

        return new PageResponse<>(dtoList, pageNumber, pageSize, total);
    }

    // 設定管理員後台: 操作紀錄搜尋
    @Override
    public PageResponse<AdminOperationLogDto> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize) {
        // ----------撈資料----------
        // 只撈頁面需要用到的欄位，避免撈出整張表
        Specification<AdminOperationLog> spec = AdminLogSpecification.build(request);
        List<Tuple> rows = logRepo.findLogListTuples(spec, pageNumber, pageSize);
        // 另外查詢符合條件的總筆數
        long total = logRepo.count(spec);
        // ----------設定回傳資料----------
        List<AdminOperationLogDto> dtoList = new ArrayList<>();
        for (Tuple row : rows) {
            AdminOperationLogDto dtoItem = new AdminOperationLogDto(
                    row.get("id", Long.class),
                    row.get("adminName", String.class),
                    row.get("operationType", AdminOperationType.class),
                    toTargetTypeForFront(row.get("targetType", AdminTargetType.class), row.get("targetUserRole", Role.class)),
                    row.get("targetName", String.class),
                    row.get("email", String.class),
                    row.get("createdAt", LocalDateTime.class).format(DATE_TIME_FORMATTER),
                    row.get("content", String.class));

            dtoList.add(dtoItem);
        }

        PageResponse<AdminOperationLogDto> response = new PageResponse<>(dtoList, pageNumber, pageSize, total);
        return response;
    }

    @Override
    @Transactional
    public UserStatusChangeDto setUserAccountDisable(Long userId, String operatorEmail, Role operatorRole) {
        if (userId == null) {
            throw new IllegalArgumentException("請提供使用者id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        UserAccountStatusProjection target = userRepo.findAccountStatusById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的帳號: id:"+ userId));

        UserStatus newStatus = target.status();
        String targetLabel = target.contactName() != null ? target.contactName() : target.email();

        if (target.status() == UserStatus.ACTIVE) {
            userRepo.updateStatusIfCurrent(userId, UserStatus.ACTIVE, UserStatus.DISABLED);
            newStatus = UserStatus.DISABLED;
        }else{
            throw new IllegalArgumentException(targetLabel + "的帳號狀態不可執行此操作");
        }

        saveAdminLog(
                userRepo.getReferenceById(admin.id()), AdminOperationType.ACCOUNT_DISABLED, AdminTargetType.USER,
                userId, targetLabel, admin.adminName() + "停用" + targetLabel + "的帳號");

        return new UserStatusChangeDto(target.contactName(), target.email(), newStatus);
    }

    @Override
    @Transactional
    public UserStatusChangeDto setUserAccountRestore(Long userId, String operatorEmail, Role operatorRole) {
        if (userId == null) {
            throw new IllegalArgumentException("請提供使用者id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        UserAccountStatusProjection target = userRepo.findAccountStatusById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的帳號: id:"+ userId));

        UserStatus newStatus = target.status();
        String targetLabel = target.contactName() != null ? target.contactName() : target.email();

        if (target.status() == UserStatus.DISABLED) {
            userRepo.updateStatusIfCurrent(userId, UserStatus.DISABLED, UserStatus.ACTIVE);
            newStatus = UserStatus.ACTIVE;
        }else{
            throw new IllegalArgumentException(targetLabel + "的帳號狀態不可執行此操作");
        }

        saveAdminLog(
                userRepo.getReferenceById(admin.id()), AdminOperationType.ACCOUNT_RESTORED, AdminTargetType.USER,
                userId, targetLabel, admin.adminName() + "恢復" + targetLabel + "的帳號");

        return new UserStatusChangeDto(target.contactName(), target.email(), newStatus);
    }

    /**
     * 批次停用/恢復使用者帳號。
     * 每一筆各自在獨立交易中處理並各寫一筆操作紀錄，單筆失敗只會回滾該筆，不影響其他筆。
     */
    @Override
    public BatchUserStatusResultDto batchChangeUserAccountStatus(
            List<Long> userIds, boolean disable, String reason, String operatorEmail, Role operatorRole) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("請至少選擇一位使用者");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("請填寫操作原因");
        }
        String trimmedReason = reason.trim();
        if (trimmedReason.length() > BATCH_REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("操作原因不可超過" + BATCH_REASON_MAX_LENGTH + "字");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<BatchUserStatusResultDto.Success> succeeded = new ArrayList<>();
        List<BatchUserStatusResultDto.Failure> failed = new ArrayList<>();

        // 去除重複id，保留使用者選擇的順序
        for (Long userId : new java.util.LinkedHashSet<>(userIds)) {
            try {
                BatchUserStatusResultDto.Success ok =
                        tx.execute(status -> changeOneUserStatus(userId, admin, disable, trimmedReason));
                succeeded.add(ok);
            } catch (BatchItemException e) {
                failed.add(new BatchUserStatusResultDto.Failure(userId, e.label, e.getMessage()));
            } catch (Exception e) {
                failed.add(new BatchUserStatusResultDto.Failure(userId, null, "系統錯誤，此筆未處理"));
            }
        }
        return new BatchUserStatusResultDto(succeeded, failed);
    }

    /** 單筆處理(須在交易中呼叫)；不符合條件時丟 BatchItemException，使該筆交易回滾 */
    private BatchUserStatusResultDto.Success changeOneUserStatus(
            Long userId, AdminLookupProjection admin, boolean disable, String reason) {
        if (userId == null) {
            throw new BatchItemException(null, "使用者id為空");
        }
        UserAccountStatusProjection target = userRepo.findAccountStatusById(userId)
                .orElseThrow(() -> new BatchItemException(null, "找不到指定的帳號"));
        String label = target.contactName() != null ? target.contactName() : target.email();

        if (userId.equals(admin.id())) {
            throw new BatchItemException(label, "不可變更自己的帳號狀態");
        }
        Role targetRole = userRepo.findById(userId).map(User::getRole).orElse(null);
        if (targetRole == Role.ADMIN) {
            throw new BatchItemException(label, "不可變更管理員帳號");
        }

        UserStatus from = disable ? UserStatus.ACTIVE : UserStatus.DISABLED;
        UserStatus to = disable ? UserStatus.DISABLED : UserStatus.ACTIVE;
        if (target.status() != from) {
            throw new BatchItemException(label, disable ? "帳號不是正常狀態，無法停用" : "帳號不是停用狀態，無法恢復");
        }
        // 條件式更新：狀態已被別人改過時影響筆數為 0
        if (userRepo.updateStatusIfCurrent(userId, from, to) != 1) {
            throw new BatchItemException(label, "帳號狀態已變更，請重新載入後再操作");
        }

        saveAdminLog(
                userRepo.getReferenceById(admin.id()),
                disable ? AdminOperationType.ACCOUNT_DISABLED : AdminOperationType.ACCOUNT_RESTORED,
                AdminTargetType.USER, userId, label,
                admin.adminName() + (disable ? "批次停用" : "批次恢復") + label + "的帳號，原因：" + reason);
        return new BatchUserStatusResultDto.Success(userId, label, to);
    }

    /** 批次單筆失敗的內部例外，帶出顯示名稱供結果畫面使用 */
    private static class BatchItemException extends RuntimeException {
        private final String label;

        BatchItemException(String label, String message) {
            super(message);
            this.label = label;
        }
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventApprove(Long eventId, String operatorEmail, Role operatorRole, String note) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        int updatedRows = note == null || note.isBlank()
                ? eventRepo.updateWorkflowStatusIfCurrent(
                        eventId, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING)
                : eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                        eventId, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING, note);
        if (updatedRows != 1) {
            throw new IllegalArgumentException(event.title() + "狀態已變更，請重新載入後再操作");
        }

        saveNotification(
                userRepo.getReferenceById(event.organizerId()), NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_APPROVED, NotificationTargetType.MARKET_EVENT, eventId,
                "活動審核通過", "活動「" + event.title() + "」已通過管理員審核，接下來將建置攤位地圖");

        saveAdminLog(
                userRepo.getReferenceById(admin.id()), AdminOperationType.ACTIVITY_REVIEW, AdminTargetType.MARKET_EVENT,
                eventId, event.title(), admin.adminName() + "同意" + event.title() + "申請");

        return new EventStatusChangeDto(event.title(), EventStatus.MAP_BUILDING);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventRevision(Long eventId, String operatorEmail, Role operatorRole, String note) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("請提供補件原因");
        }

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        int updatedRows = eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                eventId, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, note);
        if (updatedRows != 1) {
            throw new IllegalArgumentException(event.title() + "狀態已變更，請重新載入後再操作");
        }

        saveNotification(
                userRepo.getReferenceById(event.organizerId()), NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_REVISION_REQUIRED, NotificationTargetType.MARKET_EVENT, eventId,
                "活動需要補件", "活動「" + event.title() + "」需要補件；原因：" + note + "。請修改後重新送出審核");

        saveAdminLog(
                userRepo.getReferenceById(admin.id()), AdminOperationType.REQUEST_REVISION, AdminTargetType.MARKET_EVENT,
                eventId, event.title(), admin.adminName() + "退回" + event.title() + "申請, 原因:" + note);

        return new EventStatusChangeDto(event.title(), EventStatus.REVISION_REQUIRED);
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventMapComplete(Long eventId, String operatorEmail, Role operatorRole) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.MAP_BUILDING) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        MarketEvent marketEvent = eventRepo.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));
        Integer maxBooths = marketEvent.getMaxBooths();
        if (maxBooths == null || maxBooths <= 0) {
            throw new IllegalArgumentException(event.title() + "尚未設定有效的攤位總數");
        }

        List<EventStallZone> zones = eventStallZoneRepo.findByMarketEventId(eventId).stream()
                .sorted(Comparator.comparing(EventStallZone::getZoneName))
                .toList();
        if (zones.isEmpty()) {
            throw new IllegalArgumentException(event.title() + "尚未建立攤位分區");
        }

        Set<String> zoneNames = new HashSet<>();
        long plannedStallCount = 0;
        for (EventStallZone zone : zones) {
            String zoneName = zone.getZoneName() == null ? "" : zone.getZoneName().trim();
            if (!zoneName.matches("^[A-Z] 區$") || !zoneNames.add(zoneName)) {
                throw new IllegalArgumentException(event.title() + "的分區名稱必須為 A 區至 Z 區且不可重複");
            }
            if (zone.getStallCount() <= 0) {
                throw new IllegalArgumentException(event.title() + "的分區攤位數必須大於 0");
            }
            plannedStallCount += zone.getStallCount();
        }
        if (plannedStallCount != maxBooths) {
            throw new IllegalArgumentException(
                    event.title() + "的分區攤位數合計為 " + plannedStallCount
                            + "，必須等於活動攤位總數 " + maxBooths);
        }

        long existingStallCount = eventStallRepo.countByMarketEvent_Id(eventId);
        if (existingStallCount != 0 && existingStallCount != maxBooths) {
            throw new IllegalArgumentException(
                    event.title() + "的互動式攤位資料不完整，目前已建立 "
                            + existingStallCount + " / " + maxBooths + " 個攤位");
        }

        int updatedRows = eventRepo.updateWorkflowStatusIfCurrent(
                eventId, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH);
        if (updatedRows != 1) {
            throw new IllegalArgumentException(event.title() + "狀態已變更，請重新載入後再操作");
        }

        if (existingStallCount == 0) {
            List<EventStall> stalls = buildEventStalls(marketEvent, zones);
            eventStallRepo.saveAllAndFlush(stalls);
            long createdStallCount = eventStallRepo.countByMarketEvent_Id(eventId);
            if (createdStallCount != maxBooths) {
                throw new IllegalStateException(
                        event.title() + "攤位建立失敗，目前已建立 "
                                + createdStallCount + " / " + maxBooths + " 個攤位");
            }
        }

        saveNotification(
                userRepo.getReferenceById(event.organizerId()), NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_MAP_COMPLETED, NotificationTargetType.MARKET_EVENT, eventId,
                "活動攤位地圖完成", "活動「" + event.title() + "」的攤位地圖已建置完成，可前往活動詳情確認");

        String organizerLabel = event.organizerContactName() != null ? event.organizerContactName() : "主辦方";
        String eventTitleLabel = event.title() != null ? event.title() : "活動";

        saveAdminLog(
                userRepo.getReferenceById(admin.id()), AdminOperationType.MAP_BUILD_COMPLETED, AdminTargetType.MARKET_EVENT,
                eventId, event.title(), admin.adminName() + "通知主辦方" + organizerLabel + " " + eventTitleLabel + "地圖建置完成");

        return new EventStatusChangeDto(event.title(), EventStatus.READY_TO_PUBLISH);
    }

    private List<EventStall> buildEventStalls(MarketEvent marketEvent, List<EventStallZone> zones) {
        List<EventStall> stalls = new ArrayList<>();
        for (EventStallZone zone : zones) {
            String prefix = zone.getZoneName().substring(0, 1);
            int numberWidth = Math.max(2, String.valueOf(zone.getStallCount()).length());
            for (int number = 1; number <= zone.getStallCount(); number++) {
                EventStall stall = new EventStall();
                stall.setMarketEvent(marketEvent);
                stall.setZone(zone);
                stall.setStallNo(prefix + String.format("%0" + numberWidth + "d", number));
                stall.setStatus(com.example.demo.enums.status.StallStatus.AVAILABLE);
                stalls.add(stall);
            }
        }
        return stalls;
    }

    @Override
    @Transactional
    public EventStatusChangeDto setEventUnpublish(Long eventId, String operatorEmail, Role operatorRole, String note) {
        if (eventId == null) {
            throw new IllegalArgumentException("請提供活動id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        EventApprovalProjection event = eventRepo.findApprovalStatusById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的活動"));

        if (event.workflowStatus() != WorkflowStatus.UNPUBLISH_REQUESTED) {
            throw new IllegalArgumentException(event.title() + "當前狀態不可執行此操作");
        }

        if (eventApplicationRepo.existsUnprocessedPayment(eventId)) {
            throw new IllegalArgumentException("當前活動尚有未處理款項");
        }

        User adminRef = userRepo.getReferenceById(admin.id());

        Long unpublishRequestId = eventUnpublishRequestRepo
                .findLatestRequestIdByEventIdAndStatus(eventId, UnpublishRequestStatus.PENDING)
                .orElse(null);

        if (unpublishRequestId == null) {
            adminExceptionNotificationService.notifyMissingUnpublishRequest(
                    adminRef,
                    eventId,
                    event.title());

            throw new IllegalArgumentException("找不到該活動的下架申請");
        }

        int eventUpdatedRows = eventRepo.updateWorkflowStatusIfCurrent(
                eventId, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED);
        if (eventUpdatedRows != 1) {
            throw new IllegalArgumentException(event.title() + "狀態已變更，請重新載入後再操作");
        }

        int requestUpdatedRows = eventUnpublishRequestRepo.reviewIfCurrent(
                unpublishRequestId, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.APPROVED, note);
        if (requestUpdatedRows != 1) {
            throw new IllegalArgumentException(event.title() + "下架申請狀態已變更，請重新載入後再操作");
        }

        saveNotification(
                userRepo.getReferenceById(event.organizerId()), NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UNPUBLISHED, NotificationTargetType.MARKET_EVENT, eventId,
                "活動已下架", "活動「" + event.title() + "」的下架申請已通過，活動已下架");

        saveAdminLog(
                adminRef, AdminOperationType.EVENT_UNPUBLISH_REVIEW, AdminTargetType.EVENT_UNPUBLISH_REQUEST,
                unpublishRequestId, event.title(), admin.adminName() + "審核通過" + event.title() + "活動下架申請");

        return new EventStatusChangeDto(event.title(), EventStatus.UNPUBLISHED);
    }

    /**
     * 設定:活動下架申請退回(要求補件)，將指定下架申請單狀態設為REJECTED，
     * 並依活動品牌是否已公開過，將活動流程狀態改回PUBLISHED(尚未公開過)或FINAL_REVIEW(已公開過)<br>
     * <b>API路徑</b>: /api/admin/events/{id}/request-revision (isUnpublish=true)<br>
     * @param unpublishRequestId 下架申請單id
     * @param operatorEmail 操作者(管理員)email
     * @param operatorRole 操作者角色，須為{@link Role#ADMIN}
     * @param note 補件原因
     * @return 活動名稱、活動新狀態
     */
    @Override
    @Transactional
    public EventStatusChangeDto setEventUnpublishRequestReject(
            Long unpublishRequestId, String operatorEmail, Role operatorRole, String note) {
        if (unpublishRequestId == null) {
            throw new IllegalArgumentException("請提供下架申請id");
        }
        AdminLookupProjection admin = validateAdminOperator(operatorEmail, operatorRole);

        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("請提供補件原因");
        }

        EventUnpublishReviewProjection review = eventUnpublishRequestRepo.findReviewInfoById(unpublishRequestId)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的下架申請"));

        if (review.requestStatus() != UnpublishRequestStatus.PENDING) {
            throw new IllegalArgumentException("該下架申請單不可進行此操作");
        }
        if (review.eventId() == null) {
            throw new IllegalArgumentException("找不到該下架申請對應的活動");
        }
        if (review.eventStatus() != WorkflowStatus.UNPUBLISH_REQUESTED) {
            throw new IllegalArgumentException(review.eventName() + "當前狀態不可執行此操作");
        }

        User adminRef = userRepo.getReferenceById(admin.id());
        LocalDateTime now = LocalDateTime.now();
        WorkflowStatus newWorkflowStatus = review.brandPublicAt() != null
                && !review.brandPublicAt().isAfter(now)
                        ? WorkflowStatus.FINAL_REVIEW
                        : WorkflowStatus.PUBLISHED;

        int eventUpdatedRows = eventRepo.updateWorkflowStatusIfCurrent(
                review.eventId(), WorkflowStatus.UNPUBLISH_REQUESTED, newWorkflowStatus);
        if (eventUpdatedRows != 1) {
            throw new IllegalArgumentException(review.eventName() + "狀態已變更，請重新載入後再操作");
        }

        int requestUpdatedRows = eventUnpublishRequestRepo.reviewIfCurrent(
                unpublishRequestId, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.REJECTED, note);
        if (requestUpdatedRows != 1) {
            throw new IllegalArgumentException(review.eventName() + "下架申請狀態已變更，請重新載入後再操作");
        }

        saveNotification(
                userRepo.getReferenceById(review.userId()), NotificationCategory.EVENT_CHANGE,
                NotificationType.EVENT_UNPUBLISH_REQUEST_REVISION_REQUIRED, NotificationTargetType.EVENT_UNPUBLISH_REQUEST,
                unpublishRequestId, "補件通知",
                "活動「" + review.eventName() + "」的下架申請需要補件；原因：" + note + "。請修改後重新送出審核");

        saveAdminLog(
                adminRef, AdminOperationType.REQUEST_REVISION, AdminTargetType.EVENT_UNPUBLISH_REQUEST,
                unpublishRequestId, review.eventName(), admin.adminName() + "退回" + review.eventName() + "下架申請, 原因:" + note);

        EventStatus newEventStatus = newWorkflowStatus == WorkflowStatus.FINAL_REVIEW
                ? EventStatus.BRANDS_PUBLISHED
                : EventStatus.PUBLISHED;
        return new EventStatusChangeDto(review.eventName(), newEventStatus);
    }

    // 處理通知中心:通知建立時間格式映射
    private AdminNoticeDto toAdminNoticeDto(AdminNoticeProjection notice) {
        String time = notice.createdAt() == null ? null : notice.createdAt().format(DATE_TIME_FORMATTER);

        return new AdminNoticeDto(
                notice.id(),
                notice.type(),
                notice.targetType(),
                notice.targetId(),
                notice.title(),
                NotificationContentSanitizer.sanitize(notice.content()),
                notice.isRead(),
                time);
    }

    // 處理活動狀態變動紀錄:說明的文字映射
    private StatusLog toStatusLog(EventStatusLogProjection log) {
        String dateTime = log.reqAt() == null ? null : log.reqAt().format(DATE_TIME_FORMATTER);
        WorkflowStatus status = log.newStatus() == null ? null : WorkflowStatus.valueOf(log.newStatus());
        String description = log.newStatus() == null
                ? null
                : messageSource.getMessage("workflow-status." + log.newStatus(), null, Locale.TAIWAN);
        String operator = log.role() == Role.ADMIN ? log.adminName() : log.orgName();

        return new StatusLog(dateTime, status, description, log.role(), operator);
    }

    /** 依審核狀態、付款狀態、是否取消，轉換為前端顯示的報名狀態文字 */
    private String resolveRegStatus(Boolean isCancelled, ReviewStatus reviewStatus, PaymentStatus paymentStatus) {
        if (Boolean.TRUE.equals(isCancelled)) {
            return "已取消";
        }
        if (reviewStatus == null) {
            return "";
        }
        switch (reviewStatus) {
            case PENDING:
                return "待審核";
            case APPROVED:
                if (paymentStatus == PaymentStatus.PENDING || paymentStatus == PaymentStatus.FAILED) {
                    return "待付款";
                }
                if (paymentStatus == PaymentStatus.PAID) {
                    return "報名完成";
                }
                if (paymentStatus == PaymentStatus.EXPIRED) {
                    return "報名失敗";
                }
                return "";
            case REJECTED:
                return "報名失敗";
            default:
                return "";
        }
    }

    /** 若該報名有退款紀錄，依是否已有退款完成時間判斷為已退款/退款中，否則沿用原本的付款狀態 */
    private String resolvePaymentStatus(
            Long applicationId,
            PaymentStatus paymentStatus,
            Map<Long, Boolean> hasRefundByApplication,
            Map<Long, Boolean> hasRefundedAtByApplication) {
        boolean hasRefund = hasRefundByApplication.getOrDefault(applicationId, false);
        if (hasRefund) {
            boolean hasRefundedAt = hasRefundedAtByApplication.getOrDefault(applicationId, false);
            return hasRefundedAt ? RefundStatus.REFUNDED.getStatus() : RefundStatus.REFUNDING.getStatus();
        }
        return paymentStatus == null ? null : paymentStatus.getStatus();
    }

    /**
     * 依原始targetType、操作對象角色(僅targetType=USER時有值)換算成前端顯示用的AdminTargetTypeForFront。<br>
     * AdminTargetTypeForFront沒有對應任何資料庫欄位，Hibernate無法在CriteriaBuilder裡處理這個型別，
     * 因此改成在tuple查詢只撈原始的targetType/role，這裡用一般Java程式碼換算。
     */
    private AdminTargetTypeForFront toTargetTypeForFront(AdminTargetType targetType, Role targetUserRole) {
        return switch (targetType) {
            case SYSTEM_SETTING -> AdminTargetTypeForFront.SYSTEM_SETTING;
            case MARKET_EVENT, EVENT_UNPUBLISH_REQUEST -> AdminTargetTypeForFront.MARKET_EVENT;
            case USER -> switch (targetUserRole) {
                case ORGANIZER -> AdminTargetTypeForFront.ORGANIZER;
                case VENDOR -> AdminTargetTypeForFront.VENDOR;
                case ADMIN -> null;
                case null -> null;
            };
        };
    }

    /** 驗證操作者為有效管理員，並回傳該管理員資料 */
    private AdminLookupProjection validateAdminOperator(String operatorEmail, Role operatorRole) {
        if (operatorEmail == null || operatorEmail.isBlank() || operatorRole != Role.ADMIN) {
            throw new IllegalArgumentException("權限不足，請重新登入管理員帳號再操作");
        }
        return userRepo.findAdminLookupByEmailAndRole(operatorEmail, Role.ADMIN)
                .orElseThrow(() -> new IllegalArgumentException("找不到該管理員"));
    }

    /** 建立並儲存一筆管理員操作紀錄 */
    private void saveAdminLog(
            User admin, AdminOperationType operationType, AdminTargetType targetType,
            Long targetId, String targetLabel, String content) {
        AdminOperationLog adminLog = new AdminOperationLog();
        adminLog.setUser(admin);
        adminLog.setOperationType(operationType);
        adminLog.setTargetType(targetType);
        adminLog.setTargetId(targetId);
        adminLog.setTargetLabel(targetLabel);
        adminLog.setContent(content);
        logRepo.save(adminLog);
    }

    /** 建立並儲存一筆通知 */
    private void saveNotification(
            User user, NotificationCategory category, NotificationType type, NotificationTargetType targetType,
            Long targetId, String title, String content) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setCategory(category);
        notification.setType(type);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTitle(title);
        notification.setContent(NotificationContentSanitizer.sanitize(content));
        notificationRepo.save(notification);
    }

    /**
     * 將MarketEvent轉換成EventStatus
     * 
     * @param data :MarketEvent
     * @return EventStatus
     * @throws IllegalArgumentException 型別不符時拋出
     */
    @Override
    public EventStatus changeToEventStatus(Object data) throws IllegalArgumentException {
        if (data instanceof MarketEvent) {
            MarketEvent entity = (MarketEvent) data;

            return checkEventStatus(
                    entity.getWorkflowStatus(),
                    entity.getRegistrationStartAt(),
                    entity.getRegistrationEndAt(),
                    entity.getBrandPublicAt(),
                    entity.getStartAt(),
                    entity.getEndAt(),
                    entity.getMaxBooths(),
                    entity.getEventApplications() == null ? 0 : entity.getEventApplications().size());
        }
        throw new IllegalArgumentException("data須符合型別類型MarketEvent");
    }
}
