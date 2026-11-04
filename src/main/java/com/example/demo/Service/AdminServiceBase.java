package com.example.demo.Service;

import org.springframework.lang.NonNull;

import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.response.PageResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.AdminEventDetailDto;
import com.example.demo.dto.response.admin.AdminEventListDto;
import com.example.demo.dto.response.admin.AdminNoticeDto;
import com.example.demo.dto.response.admin.AdminOperationLogDto;
import com.example.demo.dto.response.admin.AdminOrgDetailDto;
import com.example.demo.dto.response.admin.AdminOrgEventManagementDto;
import com.example.demo.dto.response.admin.AdminUserListDto;
import com.example.demo.dto.response.admin.AdminUserLoginDto;
import com.example.demo.dto.response.admin.AdminVenderDetailDto;
import com.example.demo.dto.response.admin.AdminVenderRegDto;
import com.example.demo.dto.response.admin.EventStatusChangeDto;
import com.example.demo.dto.response.admin.StatusLog;
import com.example.demo.dto.response.admin.UserStatusChangeDto;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.type.Role;

import jakarta.annotation.Nonnull;

public abstract class AdminServiceBase {

        /** 取得管理員後台: 首頁資料統計部分 */
        public abstract AdminDashboardDto getDashboardResponse(String operatorEmail);

        /** 取得管理員後台: 通知中心 */
        public abstract PageResponse<AdminNoticeDto> getNotice(NotificationCategory category, Boolean isOnlyUnread, int pageNumber, int pageSize, String operatorEmail);

        /** 取得管理員後台: 活動搜尋 */
        public abstract PageResponse<AdminEventListDto> getEventsList(AdminEventSearchDto request, int pageNumber, int pageSize);

        /** 取得管理員後台: 活動詳細 */
        public abstract AdminEventDetailDto getEventDetail(@NonNull Long eventId, int pageSize);

        /** 取得管理員後台: 活動詳細:活動狀態變動紀錄 */
        public abstract PageResponse<StatusLog> getEventStatusLogs(@NonNull Long eventId, int pageNumber, int pageSize);

        /** 取得管理員後台: 使用者搜尋 */
        public abstract PageResponse<AdminUserListDto> getUserList(AdminUserSearchDto request, int pageNumber, int pageSize);

        /** 取得管理員後台: 攤主詳細 */
        public abstract AdminVenderDetailDto getVenderDetail(@NonNull Long userId, int pageSize);

        /** 取得管理員後台: 攤主詳細: 活動報名紀錄 */
        public abstract PageResponse<AdminVenderRegDto> getVenderRegLogs(@Nonnull Long userId, int pageNumber, int pageSize);

        /** 取得管理員後台: 主辦方詳細 */
        public abstract AdminOrgDetailDto getOrganizerDetail(@Nonnull Long userId, int pageSize);

        /** 取得管理員後台: 主辦方詳細 :活動管理紀錄 */
        public abstract PageResponse<AdminOrgEventManagementDto> getOrgEventLogs(@Nonnull Long userId, int pageNumber, int pageSize);

        /** 取得管理員後台: 使用者詳細 :使用者登入紀錄 */
        public abstract PageResponse<AdminUserLoginDto> getUserLoginLogs(@Nonnull Long userId, int pageNumber, int pageSize);

        /** 取得管理員後台: 操作紀錄搜尋 */
        public abstract PageResponse<AdminOperationLogDto> getLogs(AdminLogSearchDto request, int pageNumber, int pageSize);

        /** 設定使用者帳號停用 */
        public abstract UserStatusChangeDto setUserAccountDisable(Long userId, String operatorEmail, Role operatorRole);

        /** 設定使用者帳號復原 */
        public abstract UserStatusChangeDto setUserAccountRestore(Long userId, String operatorEmail, Role operatorRole);

        /** 批次停用(disable=true)或恢復(disable=false)使用者帳號，逐筆處理並回傳成功/失敗明細 */
        public abstract com.example.demo.dto.response.admin.BatchUserStatusResultDto batchChangeUserAccountStatus(
                java.util.List<Long> userIds, boolean disable, String reason, String operatorEmail, Role operatorRole);

        /** 設定活動審核通過 */
        public abstract EventStatusChangeDto setEventApprove(Long userId, String operatorEmail, Role operatorRole, String note);

        /** 設定活動要求補件 */
        public abstract EventStatusChangeDto setEventRevision(Long userId, String operatorEmail, Role operatorRole, String note);

        /** 設定活動下架申請退回(要求補件) */
        public abstract EventStatusChangeDto setEventUnpublishRequestReject(Long unpublishRequestId, String operatorEmail, Role operatorRole, String note);

        /** 設定地圖建置完成 */
        public abstract EventStatusChangeDto setEventMapComplete(Long userId, String operatorEmail, Role operatorRole);

        /** 設定確認活動下架 */
        public abstract EventStatusChangeDto setEventUnpublish(Long userId, String operatorEmail, Role operatorRole, String note);

}
