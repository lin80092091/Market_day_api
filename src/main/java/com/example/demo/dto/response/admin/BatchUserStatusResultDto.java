package com.example.demo.dto.response.admin;

import java.util.List;

import com.example.demo.enums.status.UserStatus;

/**
 * 管理員: 使用者批次停用/恢復結果，逐筆列出成功與失敗
 *
 * @param succeeded 成功的使用者
 * @param failed 失敗的使用者與失敗原因
 */
public record BatchUserStatusResultDto(List<Success> succeeded, List<Failure> failed) {

    /** @param id 使用者id @param label 顯示名稱(姓名或email) @param newAccountStatus 新帳號狀態 */
    public record Success(Long id, String label, UserStatus newAccountStatus) {}

    /** @param id 使用者id @param label 顯示名稱(查不到時為null) @param reason 失敗原因 */
    public record Failure(Long id, String label, String reason) {}
}
