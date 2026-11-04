package com.example.demo.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.Service.AdminService;
import com.example.demo.Service.AdminServiceBase;
import com.example.demo.Service.JwtService;
import com.example.demo.dto.request.admin.AdminEventSearchDto;
import com.example.demo.dto.request.admin.AdminEventSearchRequest;
import com.example.demo.dto.request.admin.AdminLogSearchDto;
import com.example.demo.dto.request.admin.AdminLogsSearchRequest;
import com.example.demo.dto.request.admin.AdminNoticeSearchRequest;
import com.example.demo.dto.request.admin.AdminUserSearchDto;
import com.example.demo.dto.request.admin.AdminUserSearchRequest;
import com.example.demo.dto.request.admin.BatchUserStatusRequest;
import com.example.demo.dto.request.admin.EventRevisionRequest;
import com.example.demo.dto.response.ApiResponse;
import com.example.demo.dto.response.admin.AdminDashboardDto;
import com.example.demo.dto.response.admin.EventStatusChangeDto;
import com.example.demo.enums.notification.NotificationCategory;
import com.example.demo.enums.type.Role;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理員API", description = "提供與管理員活動審核、使用者帳號停復用相關功能")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    final AdminServiceBase service;
    final JwtService jwtService;

    AdminController(AdminService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    private final int STANDARD_PAGE_SIZE = 6;
    private final int STANDARD_NOTIFICATION_PAGE_SIZE = 8;

    /**
     * 用來獲取管理員後台: 首頁資料統計部分<br>
     * <b>API路徑</b>: /api/admin/dashboard/overview<br>
     *
     * @return ApiResponse<AdminDashboardDto>
     */
    @Operation(summary = "取得管理員後台首頁統計資料", description = "取得管理員後台首頁的資料統計總覽。")
    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminDashboardDto> getDashboardOverview(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            return ApiResponse.success("ok", service.getDashboardResponse(operatorEmail));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得首頁統計資料失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 通知中心列表資料<br>
     * <b>API路徑</b>: /api/admin/notices/search<br>
     *
     * @param authorizationHeader 管理員驗證憑證
     * @param request 通知分類、未讀篩選與分頁參數；category 為 null 時查詢全部分類，isOnlyUnread 為 true 時查詢全部分類且僅未讀，pageNumber/pageSize 為 null 時使用預設值
     * @return ApiResponse<PageResponse<AdminNoticeDto>>
     */
    @Operation(summary = "查詢通知列表", description = "查詢管理員後台的通知列表，可依分類篩選，支援分頁；category 為 null 時查詢全部分類；isOnlyUnread 為 true 時查詢全部分類且僅未讀。")
    @PostMapping("/notices/search")
    public ApiResponse<?> getNotices(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) AdminNoticeSearchRequest request) {
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        NotificationCategory category = request == null ? null : request.category();
        Boolean isOnlyUnread = request == null ? null : request.isOnlyUnread();
        int pageNumber = request == null || request.pageNumber() == null ? 1 : request.pageNumber();
        int pageSize = request == null || request.pageSize() == null ? STANDARD_NOTIFICATION_PAGE_SIZE : request.pageSize();

        try {
            String operatorEmail = jwtService.getEmail(token);
            return ApiResponse.success("ok", service.getNotice(category, isOnlyUnread, pageNumber, pageSize, operatorEmail));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得通知列表失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 活動搜尋頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/events/search<br>
     * 
     * @param request 搜尋條件與分頁參數，pageNumber從1開始計算
     * @return ApiResponse<T>
     */
    @Operation(summary = "搜尋活動列表", description = "依關鍵字、主辦方、狀態、時間區間等條件搜尋活動列表，支援分頁；request 為 null 時使用預設分頁參數查詢全部活動。")
    @PostMapping("/events/search")
    public ApiResponse<?> getEventList(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody AdminEventSearchRequest request) {
        if (request == null) {
            request = new AdminEventSearchRequest(null, null, null, null, null, 1, STANDARD_PAGE_SIZE);
        }

        try {
            AdminEventSearchDto data = new AdminEventSearchDto(
                    request.keyword(),
                    request.organizer(),
                    request.status(),
                    request.startDate(),
                    request.endDate());
            int pageNumber = request.pageNumber() == null ? 1 : request.pageNumber();
            int pageSize = request.pageSize() == null ? STANDARD_PAGE_SIZE : request.pageSize();

            return ApiResponse.success("ok", service.getEventsList(data, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            System.out.println(e);
            return ApiResponse.fail("取得活動列表失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 活動詳細頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/events/{id}<br>
     * 只傳 id 時回傳活動詳細資料；有傳 pageSize 或 pageNumber 時回傳活動狀態變動紀錄<br>
     * 
     * @param id   活動id
     * @param size 每頁筆數，未傳時預設6
     * @param page 頁碼，base-1，未傳時預設1
     * @return ApiResponse<T>
     */
    @Operation(summary = "取得活動詳細資料", description = "只傳 id 時回傳活動詳細資料；有傳 pageSize 或 pageNumber 時改回傳活動狀態變動紀錄。")
    @GetMapping("/events/{id}")
    public ApiResponse<?> getEventDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer page) {
        if (id == null) {
            return ApiResponse.fail("請提供活動id");
        }

        try {
            if (size != null || page != null) {
                int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
                int number = page != null ? page : 1;
                return ApiResponse.success("ok", service.getEventStatusLogs(id, number, pageSize));
            }

            return ApiResponse.success("ok", service.getEventDetail(id, STANDARD_PAGE_SIZE));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得活動詳細資料失敗");
        }
    }

    /**
     * 設定:活動審核通過，將指定活動的workflowStatus設為MAP_BUILDING<br>
     * <b>API路徑</b>: /api/admin/events/{id}/approve<br>
     * @param authorizationHeader
     * @param id
     * @param note 審核備註，可為null
     * @return 活動名稱、活動新狀態
     */
    @Operation(summary = "活動審核通過", description = "將指定活動的審核狀態設為通過。")
    @PostMapping("/events/{id}/approve")
    public ApiResponse<?> setEventApprove(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestBody(required = false) String note) {
        if (id == null) {
            return ApiResponse.fail("請提供活動id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.setEventApprove(id, operatorEmail, operatorRole, note));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("活動審核失敗");
        }
    }

    /**
     * 設定:活動要求補件，將指定活動的workflowStatus設為REVISION_REQUIRED；
     * 若isUnpublish為true，則{id}改為EventUnpublishRequest.id，改為退回該下架申請單(要求補件)<br>
     * <b>API路徑</b>: /api/admin/events/{id}/request-revision<br>
     * @param authorizationHeader
     * @param id isUnpublish為false時為活動id，為true時為下架申請單id
     * @param request isUnpublish 是否為下架申請退回；note 補件原因
     * @return 活動名稱、活動新狀態
     */
    @Operation(summary = "活動要求補件", description = "將指定活動的審核狀態設為要求補件，或退回指定的下架申請單。")
    @PostMapping("/events/{id}/request-revision")
    public ApiResponse<?> setEventRevision(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestBody(required = false) EventRevisionRequest request
        ) {
        if (id == null) {
            return ApiResponse.fail("請提供id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        String note = request == null ? null : request.note();
        if (note == null || note.isBlank()) {
            return ApiResponse.fail("請提供補件原因");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            boolean isUnpublish = request != null && Boolean.TRUE.equals(request.isUnpublish());
            EventStatusChangeDto result = isUnpublish
                    ? service.setEventUnpublishRequestReject(id, operatorEmail, operatorRole, note)
                    : service.setEventRevision(id, operatorEmail, operatorRole, note);
            return ApiResponse.success("ok", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("活動要求補件失敗");
        }
    }

    @Operation(summary = "活動地圖建置完成", description = "將指定活動的地圖建置狀態設為完成。")
    @PostMapping("/events/{id}/map-complete")
    public ApiResponse<?> setEventMapComplete(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        if (id == null) {
            return ApiResponse.fail("請提供活動id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.setEventMapComplete(id, operatorEmail, operatorRole));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("通知主辦方攤位地圖建置完成失敗");
        }
    }

    /**
     * 設定:確認活動下架，將指定活動的workflowStatus設為UNPUBLISHED，並審核通過該活動最新一筆待審核的下架申請<br>
     * <b>API路徑</b>: /api/admin/events/{id}/unpublish-confirm<br>
     * @param authorizationHeader
     * @param id
     * @param note 審核備註，可為null
     * @return 活動名稱、活動新狀態
     */
    @Operation(summary = "確認活動下架", description = "確認將指定活動下架。")
    @PostMapping("/events/{id}/unpublish-confirm")
    public ApiResponse<?> setEventUnpublish(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestBody(required = false) String note
        ) {
        if (id == null) {
            return ApiResponse.fail("請提供活動id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.setEventUnpublish(id, operatorEmail, operatorRole, note));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("確認活動下架失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 使用者搜尋頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/users/search<br>
     * 
     * @param request 搜尋條件與分頁參數，pageNumber從1開始計算
     * @return ApiResponse<T>
     */
    @Operation(summary = "搜尋使用者列表", description = "依關鍵字、角色、帳號狀態等條件搜尋使用者列表，支援分頁；request 為 null 時使用預設分頁參數查詢全部使用者。")
    @PostMapping("/users/search")
    public ApiResponse<?> getUserList(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody AdminUserSearchRequest request) {
        if (request == null) {
            request = new AdminUserSearchRequest(null, null, null, 1, STANDARD_PAGE_SIZE);
        }

        try {
            AdminUserSearchDto data = new AdminUserSearchDto(
                    request.keyWord(),
                    request.role(),
                    request.status());
            int pageNumber = request.pageNumber() == null ? 1 : request.pageNumber();
            int pageSize = request.pageSize() == null ? STANDARD_PAGE_SIZE : request.pageSize();

            return ApiResponse.success("ok", service.getUserList(data, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得使用者列表失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 使用者詳細頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/users/{id}?role=vender<br>
     * <b>API路徑</b>: /api/admin/users/{id}?role=organizer<br>
     *
     * @param id   使用者id
     * @param size 每頁筆數，未傳時預設6
     * @return ApiResponse<T>
     */
    @Operation(summary = "取得使用者詳細資料", description = "依使用者 id 和角色取得攤主的帳號、品牌與活動報名等詳細資料或是主辦方的帳號與活動管理等詳細資料。")
    @GetMapping("/users/{id}")
    public ApiResponse<?> getUserDetail(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestParam String role,
            @RequestParam(required = false) Integer size) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        if (role == null) {
            return ApiResponse.fail("請提供使用者角色");
        }

        if (role.equals("vender")) {
            try {
                int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
                return ApiResponse.success("ok", service.getVenderDetail(id, pageSize));
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail(e.getMessage());
            } catch (Exception e) {
                return ApiResponse.fail("取得攤主詳細資料失敗");
            }
        } else if (role.equals("organizer")) {
            try {
                int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
                return ApiResponse.success("ok", service.getOrganizerDetail(id, pageSize));
            } catch (IllegalArgumentException e) {
                return ApiResponse.fail(e.getMessage());
            } catch (Exception e) {
                return ApiResponse.fail("取得主辦方詳細資料失敗");
            }
        }

        return ApiResponse.fail("該使用者角色不存在");
    }

    /**
     * 用來獲取管理員後台: 攤主詳細頁面所需資料: 活動報名紀錄<br>
     * <b>API路徑</b>: /api/admin/users/{id}/venderReg<br>
     *
     * @param id   使用者id
     * @param size 每頁筆數，未傳時預設6
     * @param page 頁碼，base-1，未傳時預設1
     * @return ApiResponse<T>
     */
    @Operation(summary = "取得攤主活動報名紀錄", description = "依使用者 id 分頁查詢攤主的活動報名紀錄。")
    @GetMapping("/users/{id}/venderReg")
    public ApiResponse<?> getVenderRegLogs(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer page) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        try {
            int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
            int pageNumber = page != null ? page : 1;
            return ApiResponse.success("ok", service.getVenderRegLogs(id, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得攤主活動報名紀錄失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 主辦方詳細頁面所需資料: 活動管理紀錄<br>
     * <b>API路徑</b>: /api/admin/users/{id}/orgEvent<br>
     *
     * @param id   使用者id
     * @param size 每頁筆數，未傳時預設6
     * @param page 頁碼，base-1，未傳時預設1
     * @return ApiResponse<T>
     */
    @Operation(summary = "取得主辦方活動管理紀錄", description = "依使用者 id 分頁查詢主辦方的活動管理紀錄。")
    @GetMapping("/users/{id}/orgEvent")
    public ApiResponse<?> getOrgEventLogs(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer page) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        try {
            int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
            int pageNumber = page != null ? page : 1;
            return ApiResponse.success("ok", service.getOrgEventLogs(id, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得主辦方活動管理紀錄失敗");
        }
    }

    /**
     * 用來獲取管理員後台: 使用者詳細頁面所需資料: 使用者登入紀錄<br>
     * <b>API路徑</b>: /api/admin/users/{id}/loginLog<br>
     *
     * @param id   使用者id
     * @param size 每頁筆數，未傳時預設6
     * @param page 頁碼，base-1，未傳時預設1
     * @return ApiResponse<T>
     */
    @Operation(summary = "取得使用者登入紀錄", description = "依使用者 id 分頁查詢使用者的登入紀錄；若該使用者為管理員則無登入紀錄頁面。")
    @GetMapping("/users/{id}/loginLog")
    public ApiResponse<?> getUserLoginLogs(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer page) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        try {
            int pageSize = size != null ? size : STANDARD_PAGE_SIZE;
            int pageNumber = page != null ? page : 1;
            return ApiResponse.success("ok", service.getUserLoginLogs(id, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得使用者登入紀錄失敗");
        }
    }

    @Operation(summary = "使用者帳號停用", description = "將指定使用者的帳號狀態設為停用。")
    @PostMapping("/users/{id}/disable")
    public ApiResponse<?> setUserAccountDisable(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.setUserAccountDisable(id, operatorEmail, operatorRole));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("使用者帳號停用失敗");
        }
    }

    @Operation(summary = "使用者帳號復原", description = "將指定使用者的帳號狀態由停用復原為正常。")
    @PostMapping("/users/{id}/restore")
    public ApiResponse<?> setUserAccountRestore(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long id) {
        if (id == null) {
            return ApiResponse.fail("請提供使用者id");
        }

        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }

        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.setUserAccountRestore(id, operatorEmail, operatorRole));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("使用者帳號復原失敗");
        }
    }

    @Operation(summary = "使用者帳號批次停用", description = "一次停用多位使用者(需填原因)，逐筆處理並回傳成功與失敗明細。")
    @PostMapping("/users/batch-disable")
    public ApiResponse<?> batchDisableUsers(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) BatchUserStatusRequest request) {
        return batchChangeUsers(authorizationHeader, request, true);
    }

    @Operation(summary = "使用者帳號批次恢復", description = "一次恢復多位使用者(需填原因)，逐筆處理並回傳成功與失敗明細。")
    @PostMapping("/users/batch-restore")
    public ApiResponse<?> batchRestoreUsers(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody(required = false) BatchUserStatusRequest request) {
        return batchChangeUsers(authorizationHeader, request, false);
    }

    private ApiResponse<?> batchChangeUsers(String authorizationHeader, BatchUserStatusRequest request, boolean disable) {
        if (request == null) {
            return ApiResponse.fail("請提供使用者id與原因");
        }
        String token = jwtService.extractTokenFromAuthorizationHeader(authorizationHeader);
        if (token == null || token.isBlank() || !jwtService.isTokenValid(token)) {
            return ApiResponse.fail("驗證憑證無效或已過期");
        }
        try {
            String operatorEmail = jwtService.getEmail(token);
            Role operatorRole = Role.fromRole(jwtService.getRole(token));
            return ApiResponse.success("ok", service.batchChangeUserAccountStatus(
                    request.userIds(), disable, request.reason(), operatorEmail, operatorRole));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail(disable ? "使用者批次停用失敗" : "使用者批次恢復失敗");
        }
    }

    /**
     * 用來獲取管理員後台: Logs搜尋頁面所需資料<br>
     * <b>API路徑</b>: /api/admin/logs/search<br>
     * 
     * @param request 搜尋條件與分頁參數，pageNumber從1開始計算
     * @return ApiResponse<T>
     */
    @Operation(summary = "搜尋操作紀錄列表", description = "依關鍵字、操作類型、對象類型、時間區間等條件搜尋管理員操作紀錄，支援分頁；request 為 null 時使用預設分頁參數查詢全部紀錄。")
    @PostMapping("/logs/search")
    public ApiResponse<?> getLogList(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody AdminLogsSearchRequest request) {
        if (request == null) {
            request = new AdminLogsSearchRequest(null, null, null, null, null, 1, STANDARD_PAGE_SIZE);
        }

        try {
            AdminLogSearchDto data = new AdminLogSearchDto(
                    request.keyWord(),
                    request.operationType(),
                    request.targetType(),
                    request.startAt(),
                    request.endAt());
            int pageNumber = request.pageNumber() == null ? 1 : request.pageNumber();
            int pageSize = request.pageSize() == null ? STANDARD_PAGE_SIZE : request.pageSize();
            return ApiResponse.success("ok", service.getLogs(data, pageNumber, pageSize));
        } catch (IllegalArgumentException e) {
            return ApiResponse.fail(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.fail("取得紀錄列表失敗");
        }
    }

}
