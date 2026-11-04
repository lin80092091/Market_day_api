package com.example.demo.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.Repository.AdminLogRepo;
import com.example.demo.Repository.EventApplicationRepo;
import com.example.demo.Repository.EventRepo;
import com.example.demo.Repository.EventStallRepo;
import com.example.demo.Repository.EventStallZoneRepo;
import com.example.demo.Repository.EventUnpublishRequestRepo;
import com.example.demo.Repository.NotificationRepo;
import com.example.demo.Repository.UserRepo;
import com.example.demo.Repository.projection.admin.AdminLookupProjection;
import com.example.demo.Repository.projection.admin.AdminNoticeProjection;
import com.example.demo.Repository.projection.admin.EventApprovalProjection;
import com.example.demo.Repository.projection.admin.EventUnpublishReviewProjection;
import com.example.demo.Repository.projection.admin.UserAccountStatusProjection;
import com.example.demo.entity.AdminOperationLog;
import com.example.demo.entity.EventStall;
import com.example.demo.entity.EventStallZone;
import com.example.demo.entity.MarketEvent;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.notification.NotificationTargetType;
import com.example.demo.enums.notification.NotificationType;
import com.example.demo.enums.status.EventStatus;
import com.example.demo.enums.status.UnpublishRequestStatus;
import com.example.demo.enums.status.UserStatus;
import com.example.demo.enums.status.WorkflowStatus;
import com.example.demo.enums.type.AdminOperationType;
import com.example.demo.enums.type.AdminTargetType;
import com.example.demo.enums.type.Role;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {
    @Mock EventRepo eventRepo;
    @Mock EventStallZoneRepo zoneRepo;
    @Mock EventStallRepo eventStallRepo;
    @Mock UserRepo userRepo;
    @Mock AdminLogRepo logRepo;
    @Mock NotificationRepo notificationRepo;
    @Mock AdminExceptionNotificationService adminExceptionNotificationService;
    @Mock EventUnpublishRequestRepo eventUnpublishRequestRepo;
    @Mock EventApplicationRepo eventApplicationRepo;
    AdminService service;

    @BeforeEach void setUp() {
        service = new AdminService();
        ReflectionTestUtils.setField(service, "eventRepo", eventRepo);
        ReflectionTestUtils.setField(service, "eventStallZoneRepo", zoneRepo);
        ReflectionTestUtils.setField(service, "eventStallRepo", eventStallRepo);
        ReflectionTestUtils.setField(service, "userRepo", userRepo);
        ReflectionTestUtils.setField(service, "logRepo", logRepo);
        ReflectionTestUtils.setField(service, "notificationRepo", notificationRepo);
        ReflectionTestUtils.setField(service, "adminExceptionNotificationService", adminExceptionNotificationService);
        ReflectionTestUtils.setField(service, "eventUnpublishRequestRepo", eventUnpublishRequestRepo);
        ReflectionTestUtils.setField(service, "eventApplicationRepo", eventApplicationRepo);
    }

    @Test void dashboardAggregatesRepositoryCounters() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(notificationRepo.countUnreadNoticesByCategory(9L, NotificationCategory.EXCEPTION)).thenReturn(8L);
        AdminNoticeProjection notice = new AdminNoticeProjection(
                101L, NotificationType.SYSTEM_EXCEPTION, NotificationTargetType.MARKET_EVENT, 1L,
                "標題", "主辦方建立新活動（活動 ID：），請確認活動內容",
                false, LocalDateTime.of(2026, 1, 1, 12, 0));
        when(notificationRepo.findAdminNotices(eq(9L), isNull(), isNull(), any(PageRequest.class)))
                .thenReturn(List.of(notice));
        when(notificationRepo.countAdminNotices(9L, null, null)).thenReturn(1L);
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.PENDING_REVIEW)).thenReturn(1);
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.MAP_BUILDING)).thenReturn(2);
        when(eventRepo.countByWorkflowStatus(WorkflowStatus.UNPUBLISH_REQUESTED)).thenReturn(3);
        when(userRepo.countByRoleAndStatus(Role.ORGANIZER, UserStatus.ACTIVE)).thenReturn(4);
        when(userRepo.countByRoleAndStatus(Role.VENDOR, UserStatus.ACTIVE)).thenReturn(5);
        when(eventRepo.countByEventInPlatform(any())).thenReturn(6);
        when(eventRepo.countByEventStatusIsACTIVE(any())).thenReturn(7);
        var result = service.getDashboardResponse("op@test.com");
        assertThat(result.pendingReview()).isEqualTo(1);
        assertThat(result.mapBuilding()).isEqualTo(2);
        assertThat(result.pendingUnpublish()).isEqualTo(3);
        assertThat(result.systemWarning()).isEqualTo(8);
        assertThat(result.totalOrganizer()).isEqualTo(4);
        assertThat(result.totalVender()).isEqualTo(5);
        assertThat(result.totalActivity()).isEqualTo(6);
        assertThat(result.active()).isEqualTo(7);
        assertThat(result.notices()).hasSize(1);
        assertThat(result.notices().get(0).id()).isEqualTo(101L);
        assertThat(result.notices().get(0).targetId()).isEqualTo(1L);
        assertThat(result.notices().get(0).content()).isEqualTo("主辦方建立新活動，請確認活動內容");
    }

    @Test void dashboardRejectsWhenOperatorIsNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDashboardResponse("op@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
    }

    @Test void changeStatusRejectsUnsupportedObject() {
        assertThatThrownBy(() -> service.changeToEventStatus("not an event"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void setUserAccountDisableRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.VENDOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, logRepo);
    }

    @Test void setUserAccountDisableThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountDisableThrowsWhenTargetUserNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的帳號: id:1");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountDisableDisablesActiveUserAndWritesOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", "攤主小華")));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        var result = service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN);

        verify(userRepo).updateStatusIfCurrent(1L, UserStatus.ACTIVE, UserStatus.DISABLED);
        assertThat(result.userName()).isEqualTo("攤主小華");
        assertThat(result.userEmail()).isEqualTo("vendor@test.com");
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.DISABLED);

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        AdminOperationLog savedLog = captor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_DISABLED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("攤主小華");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明停用攤主小華的帳號");
    }

    @Test void setUserAccountDisableRejectsWhenTargetAlreadyNotActive() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", "攤主小華")));
        assertThatThrownBy(() -> service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("帳號狀態不可執行此操作");
        verify(userRepo, never()).updateStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo);
    }

    // ---------- 批次停用/恢復 ----------

    private void stubBatchAdmin() {
        ReflectionTestUtils.setField(service, "transactionManager",
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
    }

    private User userWithRole(Role role) {
        User u = new User();
        u.setRole(role);
        return u;
    }

    @Test void batchDisableRequiresReason() {
        assertThatThrownBy(() -> service.batchChangeUserAccountStatus(
                java.util.List.of(1L), true, "  ", "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請填寫操作原因");
        verifyNoInteractions(userRepo, logRepo);
    }

    @Test void batchDisableRequiresAtLeastOneUser() {
        assertThatThrownBy(() -> service.batchChangeUserAccountStatus(
                java.util.List.of(), true, "違規", "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請至少選擇一位使用者");
    }

    @Test void batchDisableReportsSuccessAndFailurePerUserAndLogsOnlySuccess() {
        stubBatchAdmin();
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        // 1: 正常 -> 成功
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "a@test.com", "小華")));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(Role.VENDOR)));
        when(userRepo.updateStatusIfCurrent(1L, UserStatus.ACTIVE, UserStatus.DISABLED)).thenReturn(1);
        // 2: 已停用 -> 失敗
        when(userRepo.findAccountStatusById(2L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(2L, UserStatus.DISABLED, "b@test.com", null)));
        when(userRepo.findById(2L)).thenReturn(Optional.of(userWithRole(Role.VENDOR)));
        // 3: 不存在 -> 失敗
        when(userRepo.findAccountStatusById(3L)).thenReturn(Optional.empty());
        // 4: 管理員 -> 失敗
        when(userRepo.findAccountStatusById(4L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(4L, UserStatus.ACTIVE, "adm@test.com", "其他管理員")));
        when(userRepo.findById(4L)).thenReturn(Optional.of(userWithRole(Role.ADMIN)));
        // 9: 自己 -> 失敗
        when(userRepo.findAccountStatusById(9L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(9L, UserStatus.ACTIVE, "op@test.com", "管理員小明")));

        var result = service.batchChangeUserAccountStatus(
                java.util.List.of(1L, 2L, 3L, 4L, 9L, 1L), true, " 違反規範 ", "op@test.com", Role.ADMIN);

        assertThat(result.succeeded()).hasSize(1);
        assertThat(result.succeeded().get(0).id()).isEqualTo(1L);
        assertThat(result.succeeded().get(0).newAccountStatus()).isEqualTo(UserStatus.DISABLED);
        assertThat(result.failed()).extracting("id").containsExactly(2L, 3L, 4L, 9L);
        assertThat(result.failed().get(1).reason()).isEqualTo("找不到指定的帳號");

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo, org.mockito.Mockito.times(1)).save(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_DISABLED);
        assertThat(captor.getValue().getContent()).isEqualTo("管理員小明批次停用小華的帳號，原因：違反規範");
    }

    @Test void batchRestoreRestoresDisabledUser() {
        stubBatchAdmin();
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "a@test.com", "小華")));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(Role.VENDOR)));
        when(userRepo.updateStatusIfCurrent(1L, UserStatus.DISABLED, UserStatus.ACTIVE)).thenReturn(1);

        var result = service.batchChangeUserAccountStatus(
                java.util.List.of(1L), false, "申訴通過", "op@test.com", Role.ADMIN);

        assertThat(result.failed()).isEmpty();
        assertThat(result.succeeded().get(0).newAccountStatus()).isEqualTo(UserStatus.ACTIVE);
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_RESTORED);
    }

    @Test void batchDisableTreatsConcurrentStatusChangeAsFailure() {
        stubBatchAdmin();
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "a@test.com", "小華")));
        when(userRepo.findById(1L)).thenReturn(Optional.of(userWithRole(Role.VENDOR)));
        when(userRepo.updateStatusIfCurrent(1L, UserStatus.ACTIVE, UserStatus.DISABLED)).thenReturn(0);

        var result = service.batchChangeUserAccountStatus(
                java.util.List.of(1L), true, "違規", "op@test.com", Role.ADMIN);

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.failed()).hasSize(1);
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountDisableFallsBackToEmailWhenContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", null)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountDisable(1L, "op@test.com", Role.ADMIN);

        assertThat(result.userName()).isNull();
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getTargetLabel()).isEqualTo("vendor@test.com");
    }

    @Test void setUserAccountRestoreRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.VENDOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, logRepo);
    }

    @Test void setUserAccountRestoreThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountRestoreThrowsWhenTargetUserNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的帳號: id:1");
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountRestoreRestoresDisabledUserAndWritesOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", "攤主小華")));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        var result = service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN);

        verify(userRepo).updateStatusIfCurrent(1L, UserStatus.DISABLED, UserStatus.ACTIVE);
        assertThat(result.userName()).isEqualTo("攤主小華");
        assertThat(result.userEmail()).isEqualTo("vendor@test.com");
        assertThat(result.newAccountStatus()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        AdminOperationLog savedLog = captor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACCOUNT_RESTORED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.USER);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("攤主小華");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明恢復攤主小華的帳號");
    }

    @Test void setUserAccountRestoreRejectsWhenTargetAlreadyNotDisabled() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.ACTIVE, "vendor@test.com", "攤主小華")));
        assertThatThrownBy(() -> service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("帳號狀態不可執行此操作");
        verify(userRepo, never()).updateStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo);
    }

    @Test void setUserAccountRestoreFallsBackToEmailWhenContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(userRepo.findAccountStatusById(1L))
                .thenReturn(Optional.of(new UserAccountStatusProjection(1L, UserStatus.DISABLED, "vendor@test.com", null)));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());

        var result = service.setUserAccountRestore(1L, "op@test.com", Role.ADMIN);

        assertThat(result.userName()).isNull();
        ArgumentCaptor<AdminOperationLog> captor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getTargetLabel()).isEqualTo("vendor@test.com");
    }

    @Test void setEventApproveRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ORGANIZER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventApproveThrowsWhenEventNotPendingReview() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.DRAFT, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventApproveApprovesPendingReviewEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING)).thenReturn(1);
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);
        var result = service.setEventApprove(1L, "op@test.com", Role.ADMIN, null);

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING);
        verify(eventRepo, never()).updateWorkflowStatusAndReviewNoteIfCurrent(any(), any(), any(), any());
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.MAP_BUILDING);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_APPROVED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動審核通過");
        assertThat(savedNotification.getContent())
                .isEqualTo("活動「夏日市集」已通過管理員審核，接下來將建置攤位地圖");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.ACTIVITY_REVIEW);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明同意夏日市集申請");
    }

    @Test void setEventApproveWithNoteUpdatesMarketEventReviewNote() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING, "已補充審查資料")).thenReturn(1);
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.getReferenceById(5L)).thenReturn(new User());
        service.setEventApprove(1L, "op@test.com", Role.ADMIN, "已補充審查資料");

        verify(eventRepo).updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING, "已補充審查資料");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
    }

    @Test void setEventApproveStopsWhenPendingReviewWasWithdrawnConcurrently() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.MAP_BUILDING)).thenReturn(0);

        assertThatThrownBy(() -> service.setEventApprove(1L, "op@test.com", Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集狀態已變更，請重新載入後再操作");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventRevisionRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ORGANIZER, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenNoteIsBlank() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請提供補件原因");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventRevisionThrowsWhenEventNotPendingReview() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.DRAFT, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusAndReviewNoteIfCurrent(any(), any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventRevisionRequiresRevisionForPendingReviewEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, "缺少營業執照")).thenReturn(1);
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);
        var result = service.setEventRevision(1L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, "缺少營業執照");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.REVISION_REQUIRED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_REVISION_REQUIRED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動需要補件");
        assertThat(savedNotification.getContent())
                .isEqualTo("活動「夏日市集」需要補件；原因：缺少營業執照。請修改後重新送出審核");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.REQUEST_REVISION);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明退回夏日市集申請, 原因:缺少營業執照");
    }

    @Test void setEventRevisionStopsWhenPendingReviewWasWithdrawnConcurrently() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));
        when(eventRepo.updateWorkflowStatusAndReviewNoteIfCurrent(
                1L, WorkflowStatus.PENDING_REVIEW, WorkflowStatus.REVISION_REQUIRED, "缺少營業執照"))
                .thenReturn(0);

        assertThatThrownBy(() -> service.setEventRevision(
                1L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集狀態已變更，請重新載入後再操作");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ORGANIZER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteThrowsWhenEventNotMapBuilding() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.PENDING_REVIEW, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteCompletesMapBuildingEventAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));
        stubMapGenerationData(1L);
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);

        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH)).thenReturn(1);

        var result = service.setEventMapComplete(1L, "op@test.com", Role.ADMIN);

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventStall>> stallsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventStallRepo).saveAllAndFlush(stallsCaptor.capture());
        assertThat(stallsCaptor.getValue()).extracting(EventStall::getStallNo)
                .containsExactly("A01", "A02", "B01");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.READY_TO_PUBLISH);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_MAP_COMPLETED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動攤位地圖完成");
        assertThat(savedNotification.getContent())
                .isEqualTo("活動「夏日市集」的攤位地圖已建置完成，可前往活動詳情確認");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.MAP_BUILD_COMPLETED);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.MARKET_EVENT);
        assertThat(savedLog.getTargetId()).isEqualTo(1L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明通知主辦方王小華 夏日市集地圖建置完成");
    }

    @Test void setEventMapCompleteFallsBackToDefaultLabelsWhenOrganizerContactNameMissing() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, null)));
        stubMapGenerationData(1L);
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(userRepo.getReferenceById(5L)).thenReturn(new User());
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH)).thenReturn(1);

        service.setEventMapComplete(1L, "op@test.com", Role.ADMIN);

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent()).isEqualTo("管理員小明通知主辦方主辦方 夏日市集地圖建置完成");
    }

    @Test void setEventMapCompleteStopsWhenStatusChangesConcurrently() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));
        stubMapGenerationData(1L);
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.MAP_BUILDING, WorkflowStatus.READY_TO_PUBLISH)).thenReturn(0);

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集狀態已變更，請重新載入後再操作");
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteRejectsZoneTotalDifferentFromCapacity() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));
        MarketEvent marketEvent = marketEvent(1L, 3);
        when(eventRepo.findById(1L)).thenReturn(Optional.of(marketEvent));
        when(zoneRepo.findByMarketEventId(1L)).thenReturn(List.of(zone("A 區", 2)));

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集的分區攤位數合計為 2，必須等於活動攤位總數 3");

        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventMapCompleteRejectsPartiallyCreatedStalls() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));
        when(eventRepo.findById(1L)).thenReturn(Optional.of(marketEvent(1L, 3)));
        when(zoneRepo.findByMarketEventId(1L)).thenReturn(List.of(
                zone("A 區", 2), zone("B 區", 1)));
        when(eventStallRepo.countByMarketEvent_Id(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.setEventMapComplete(1L, "op@test.com", Role.ADMIN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集的互動式攤位資料不完整，目前已建立 1 / 3 個攤位");

        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventUnpublishRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ORGANIZER, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenEventNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的活動");
        verifyNoInteractions(logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsWhenEventNotUnpublishRequested() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.MAP_BUILDING, "夏日市集", 5L, "王小華")));

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verifyNoInteractions(logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishThrowsAndNotifiesAdminWhenNoPendingRequestFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", 5L, "王小華")));
        when(eventApplicationRepo.existsUnprocessedPayment(1L)).thenReturn(false);
        when(eventUnpublishRequestRepo.findLatestRequestIdByEventIdAndStatus(1L, UnpublishRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);

        assertThatThrownBy(() -> service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該活動的下架申請");

        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
        verify(logRepo, never()).save(any());

        verify(adminExceptionNotificationService)
                .notifyMissingUnpublishRequest(adminRef, 1L, "夏日市集");
    }

    @Test void setEventUnpublishApprovesRequestAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", 5L, "王小華")));
        when(eventApplicationRepo.existsUnprocessedPayment(1L)).thenReturn(false);
        when(eventUnpublishRequestRepo.findLatestRequestIdByEventIdAndStatus(1L, UnpublishRequestStatus.PENDING))
                .thenReturn(Optional.of(77L));
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED)).thenReturn(1);
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);
        when(eventUnpublishRequestRepo.reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING,
                UnpublishRequestStatus.APPROVED, "庫存已清空")).thenReturn(1);

        var result = service.setEventUnpublish(1L, "op@test.com", Role.ADMIN, "庫存已清空");

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED);
        verify(eventUnpublishRequestRepo).reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.APPROVED, "庫存已清空");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.UNPUBLISHED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_UNPUBLISHED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.MARKET_EVENT);
        assertThat(savedNotification.getTargetId()).isEqualTo(1L);
        assertThat(savedNotification.getTitle()).isEqualTo("活動已下架");
        assertThat(savedNotification.getContent())
                .isEqualTo("活動「夏日市集」的下架申請已通過，活動已下架");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.EVENT_UNPUBLISH_REVIEW);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedLog.getTargetId()).isEqualTo(77L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明審核通過夏日市集活動下架申請");
    }

    @Test void setEventUnpublishRejectsConcurrentEventStatusChangeBeforeReviewingRequest() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventRepo.findApprovalStatusById(1L))
                .thenReturn(Optional.of(new EventApprovalProjection(
                        1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", 5L, "王小華")));
        when(eventApplicationRepo.existsUnprocessedPayment(1L)).thenReturn(false);
        when(eventUnpublishRequestRepo.findLatestRequestIdByEventIdAndStatus(
                1L, UnpublishRequestStatus.PENDING)).thenReturn(Optional.of(77L));
        when(userRepo.getReferenceById(9L)).thenReturn(new User());
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.UNPUBLISHED)).thenReturn(0);

        assertThatThrownBy(() -> service.setEventUnpublish(
                1L, "op@test.com", Role.ADMIN, "庫存已清空"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集狀態已變更，請重新載入後再操作");

        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
    }

    @Test void setEventUnpublishRequestRejectRejectsWhenOperatorRoleIsNotAdmin() {
        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ORGANIZER, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("權限不足，請重新登入管理員帳號再操作");
        verifyNoInteractions(userRepo, eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenAdminNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該管理員");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenNoteIsBlank() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("請提供補件原因");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo, eventUnpublishRequestRepo);
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenRequestNotFound() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到指定的下架申請");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenRequestNotPending() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.REJECTED, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("該下架申請單不可進行此操作");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenEventIdIsNull() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, null, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("找不到該下架申請對應的活動");
        verifyNoInteractions(eventRepo, logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectThrowsWhenEventNotUnpublishRequested() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.PUBLISHED, "夏日市集", null, 5L)));

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集當前狀態不可執行此操作");
        verify(eventRepo, never()).updateWorkflowStatusIfCurrent(any(), any(), any());
        verifyNoInteractions(logRepo, notificationRepo);
        verify(eventUnpublishRequestRepo, never()).reviewIfCurrent(any(), any(), any(), any(), any());
    }

    @Test void setEventUnpublishRequestRejectRestoresToPublishedWhenBrandNotYetPublicAndWritesNotificationAndOperationLog() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集", null, 5L)));
        User adminRef = new User();
        User organizerRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(organizerRef);
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.PUBLISHED)).thenReturn(1);
        when(eventUnpublishRequestRepo.reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING,
                UnpublishRequestStatus.REJECTED, "缺少營業執照")).thenReturn(1);

        var result = service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusIfCurrent(1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.PUBLISHED);
        verify(eventUnpublishRequestRepo).reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING, UnpublishRequestStatus.REJECTED, "缺少營業執照");
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.PUBLISHED);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertThat(savedNotification.getUser()).isSameAs(organizerRef);
        assertThat(savedNotification.getCategory()).isEqualTo(NotificationCategory.EVENT_CHANGE);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.EVENT_UNPUBLISH_REQUEST_REVISION_REQUIRED);
        assertThat(savedNotification.getTargetType()).isEqualTo(NotificationTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedNotification.getTargetId()).isEqualTo(77L);
        assertThat(savedNotification.getTitle()).isEqualTo("補件通知");
        assertThat(savedNotification.getContent())
                .isEqualTo("活動「夏日市集」的下架申請需要補件；原因：缺少營業執照。請修改後重新送出審核");

        ArgumentCaptor<AdminOperationLog> logCaptor = ArgumentCaptor.forClass(AdminOperationLog.class);
        verify(logRepo).save(logCaptor.capture());
        AdminOperationLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser()).isSameAs(adminRef);
        assertThat(savedLog.getOperationType()).isEqualTo(AdminOperationType.REQUEST_REVISION);
        assertThat(savedLog.getTargetType()).isEqualTo(AdminTargetType.EVENT_UNPUBLISH_REQUEST);
        assertThat(savedLog.getTargetId()).isEqualTo(77L);
        assertThat(savedLog.getTargetLabel()).isEqualTo("夏日市集");
        assertThat(savedLog.getContent()).isEqualTo("管理員小明退回夏日市集下架申請, 原因:缺少營業執照");
    }

    @Test void setEventUnpublishRequestRejectRestoresToPublishedWhenBrandAlreadyPublic() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.UNPUBLISH_REQUESTED, "夏日市集",
                        LocalDateTime.of(2026, 1, 1, 0, 0), 5L)));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(userRepo.getReferenceById(5L)).thenReturn(new User());
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.FINAL_REVIEW)).thenReturn(1);
        when(eventUnpublishRequestRepo.reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING,
                UnpublishRequestStatus.REJECTED, "缺少營業執照")).thenReturn(1);

        var result = service.setEventUnpublishRequestReject(77L, "op@test.com", Role.ADMIN, "缺少營業執照");

        verify(eventRepo).updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.FINAL_REVIEW);
        assertThat(result.eventName()).isEqualTo("夏日市集");
        assertThat(result.newEventStatus()).isEqualTo(EventStatus.BRANDS_PUBLISHED);
    }

    @Test void setEventUnpublishRequestRejectDetectsConcurrentRequestReview() {
        when(userRepo.findAdminLookupByEmailAndRole("op@test.com", Role.ADMIN))
                .thenReturn(Optional.of(new AdminLookupProjection(9L, "管理員小明")));
        when(eventUnpublishRequestRepo.findReviewInfoById(77L)).thenReturn(Optional.of(
                new EventUnpublishReviewProjection(
                        UnpublishRequestStatus.PENDING, 1L, WorkflowStatus.UNPUBLISH_REQUESTED,
                        "夏日市集", null, 5L)));
        User adminRef = new User();
        when(userRepo.getReferenceById(9L)).thenReturn(adminRef);
        when(eventRepo.updateWorkflowStatusIfCurrent(
                1L, WorkflowStatus.UNPUBLISH_REQUESTED, WorkflowStatus.PUBLISHED)).thenReturn(1);
        when(eventUnpublishRequestRepo.reviewIfCurrent(
                77L, adminRef, UnpublishRequestStatus.PENDING,
                UnpublishRequestStatus.REJECTED, "缺少營業執照")).thenReturn(0);

        assertThatThrownBy(() -> service.setEventUnpublishRequestReject(
                77L, "op@test.com", Role.ADMIN, "缺少營業執照"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("夏日市集下架申請狀態已變更，請重新載入後再操作");

        verifyNoInteractions(logRepo, notificationRepo);
    }

    private void stubMapGenerationData(Long eventId) {
        when(eventRepo.findById(eventId)).thenReturn(Optional.of(marketEvent(eventId, 3)));
        when(zoneRepo.findByMarketEventId(eventId)).thenReturn(List.of(
                zone("A 區", 2), zone("B 區", 1)));
        when(eventStallRepo.countByMarketEvent_Id(eventId)).thenReturn(0L, 3L);
    }

    private MarketEvent marketEvent(Long eventId, int maxBooths) {
        MarketEvent event = new MarketEvent();
        event.setId(eventId);
        event.setMaxBooths(maxBooths);
        return event;
    }

    private EventStallZone zone(String name, int stallCount) {
        EventStallZone zone = new EventStallZone();
        zone.setZoneName(name);
        zone.setStallCount(stallCount);
        return zone;
    }

}
