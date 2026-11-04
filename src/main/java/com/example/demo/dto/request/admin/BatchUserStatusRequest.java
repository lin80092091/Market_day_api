package com.example.demo.dto.request.admin;

import java.util.List;

/**
 * 管理員: 使用者批次停用/恢復請求
 *
 * @param userIds 要處理的使用者id清單
 * @param reason 操作原因(必填，最長100字)
 */
public record BatchUserStatusRequest(List<Long> userIds, String reason) {}
