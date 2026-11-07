# 小集日 作業說明：重新上線＋使用者批次停用／恢復

> 本文件對應作業要求的三件事：外部網址與測試帳號、部署過程、AI 使用方式與「我搞懂了什麼」。
> 原專案的功能說明與更新日誌仍在 [README.md](README.md)，本文件只說明這次作業新增與調整的部分。

## 1. 交付資訊

| 項目 | 內容 |
|---|---|
| 外部網址（管理員端） | https://35-201-183-110.sslip.io/admin/login |
| 管理員測試帳號 | `admin@marketday.local` |
| 管理員測試密碼 | `a12345678` |
| 後端 fork | https://github.com/lin80092091/Market_day_api（分支 `feature/batch-user-status`、`deploy/gcp`） |
| 前端 fork | https://github.com/lin80092091/market_day（分支 `feature/batch-user-status`） |
| 資料庫 | 沿用 SQL Server（資料庫腳本 repo 未修改） |

> 網址僅在展示期間開放，展示結束後會刪除雲端 VM 以停止計費。

### 資料庫沿用 SQL Server 的原因
後端大量使用 SQL Server 專屬語法，換成其他資料庫必須改寫所有相關查詢，所以不換。

## 2. 新功能：使用者批次停用／恢復

### 使用方式
1. 管理員登入後進入「使用者管理」，頁面右上角有「批次管理」按鈕（預設畫面與原本相同）。
2. 按下後出現勾選欄與「批次停用」「批次恢復」按鈕，可勾選多位使用者（換頁後勾選會保留）。
3. 按下批次按鈕後必須填寫原因（最多 100 字）。
4. 送出後畫面列出成功與失敗的明細，失敗者附上原因。

### API
- `POST /api/admin/users/batch-disable`
- `POST /api/admin/users/batch-restore`
- 請求：`{ "userIds": [1, 2, 3], "reason": "原因" }`
- 回應：`{ "succeeded": [{id, label, newAccountStatus}], "failed": [{id, label, reason}] }`

### 規則
- **每位使用者各自一個交易**：單筆失敗只會回滾該筆，不影響其他筆。
- **每筆成功都寫入原本的 `admin_operation_logs`**，沿用既有的 `ACCOUNT_DISABLED` / `ACCOUNT_RESTORED` 類型，內容包含原因；失敗的不寫入。因此資料庫結構沒有任何變動。
- 判定為失敗的情況：使用者不存在、已經是目標狀態、對象是管理員、對象是自己、操作當下狀態已被他人改變。
- 重複的 id 會去重；原因空白、清單為空、原因超過 100 字會整批拒絕。

### 驗證
- 後端：新增 5 個單元測試（`AdminServiceTest`）。
- 雲端環境：實際從外部網址操作，確認成功／失敗明細與操作紀錄皆正確。

## 3. 部署過程

### 採用方式
Google Cloud 新用戶試用金（$300）開一台 Compute Engine 虛擬機，以 Docker Compose 跑四個服務：

| 服務 | 說明 |
|---|---|
| `db` | SQL Server 2022 Developer 版，不對外開放 1433 埠 |
| `db-init` | 一次性初始化：資料庫不存在才建立 |
| `api` | Spring Boot 後端（Java 21） |
| `web` | Caddy：提供 Angular 靜態檔、轉發 `/api` 與 `/images`、自動申請 HTTPS 憑證 |

- 規格：`e2-medium`（SQL Server 至少需要約 2GB 記憶體，免費的 e2-micro 跑不動）、Ubuntu 22.04、區域 `asia-east1`。
- 前後端同源：前端 `apiBaseUrl` 改為 `'/'`，由 Caddy 轉發到後端，因此網址改變時不需要改程式。
- HTTPS：使用 `35-201-183-110.sslip.io`（以 IP 產生的免費網域）搭配 Let's Encrypt，不需購買網域。
- 設定檔位於 [deploy/](deploy/)；密碼與金鑰一律放 VM 上的 `.env`，不進 repo（repo 為公開，只有 `.env.example`）。

### 方案評估
| 方案 | 結論 |
|---|---|
| 本機 SQL Server＋Cloudflare 快速通道 | 可行，但網址每次重開都會變，且展示時電腦必須開著 |
| Google Cloud 單機 VM | **採用**：真正的雲端部署，不依賴自己的電腦 |
| 換成 PostgreSQL＋Cloud Run | 不採用：需改寫所有 SQL Server 專屬語法 |

Google 的免費額度不含 Cloud SQL for SQL Server，所以資料庫也放在同一台 VM 的容器內。

### 卡在哪裡、怎麼解決
| # | 現象 | 原因 | 解決 |
|---|---|---|---|
| 1 | 資料庫初始化失敗，且留下建到一半的資料庫 | `sqlcmd` 預設關閉 `QUOTED_IDENTIFIER`，建立索引時失敗；下次啟動又看到資料庫已存在而跳過 | 加上 `-I`，並讓初始化失敗時自動刪除半成品資料庫 |
| 2 | 首頁能開，但 `/api` 全回 405 | Caddy 中單獨的 `reverse_proxy` 會被 catch-all 的 `handle` 先接走，API 請求被當成靜態檔處理 | 兩者都包進互斥的 `handle` 區塊 |
| 3 | 用環境變數建立的管理員登入後，所有寫入操作回「找不到該管理員」 | 後端自動建立管理員時只寫 `users`，沒有寫 `admin_profiles`，而管理員操作會 `JOIN admin_profiles` | 不改原團隊程式碼，改用資料庫腳本內建、且已有 profile 的 `admin@marketday.local` |
| 4 | 登入回 500，日誌篩不到原因 | `JWT_SECRET` 只有 13 字元（104 bits），HMAC-SHA 要求至少 256 bits，拋出 `WeakKeyException`；我當時只看日誌最後 60 行，全是堆疊追蹤的 `at` 行，原因在上方 | 重新產生 64 字元密鑰；之後改搜尋整份日誌；`.env.example` 補上最短長度說明 |
| 5 | 用 curl 測登入是 200，瀏覽器卻是 403 `Invalid CORS request` | 瀏覽器的 POST 會帶 `Origin: https://…`，Spring 在 Caddy 後面看到的是 `http`，判斷成跨來源；curl 預設不帶 `Origin`，所以先前測不出來 | 後端加環境變數 `SERVER_FORWARD_HEADERS_STRATEGY=framework`，信任 Caddy 傳來的 `X-Forwarded-Proto` |
| 6 | 修正已推送，VM 上卻仍然失敗 | VM 上還沒 `git pull` | 以 `git log` 與 `printenv` 確認修正有沒有真的到達 VM，再用「帶 Origin 的 curl」重現瀏覽器行為驗證 |

另外兩個小插曲：Windows Git Bash 會把 `/opt/...` 路徑轉成 Windows 路徑（用 `MSYS_NO_PATHCONV=1`）；curl 直接送中文會因編碼問題讓 JSON 解析失敗（改用 UTF-8 檔案送出）。

**心得**：第 1、2 項在本機以 Docker 先試建就抓到了，沒有等到上雲端才發現；第 5 項是本機測試方法的盲點（沒帶 `Origin`），所以最後以「模擬瀏覽器標頭」作為驗證標準。

## 4. 我給 AI 的規則，以及怎麼讓它只改需要改的地方

### 規則（`.claude/CLAUDE.md` 全文）
```
- 有任何不確定的地方都要先和我確認
- 寫給我的計畫書一律使用中文撰寫
- 修改程式碼時僅做手術式修改，不要順手重構無關的檔案。
- 在commit之前要先編譯成功過
```

### 這些規則在這次作業中的實際效果
- **不確定就先問**：資料庫要放哪（三個方案）、部署檔放哪個 repo、README 放哪、要不要推送到 GitHub，AI 都是先列出選項與建議，等我決定才動手。
- **手術式修改**：
  - 批次功能只新增檔案與新方法，沒有改動原有的單筆停用／恢復方法；沿用既有的資料表、操作類型與條件式更新方法。
  - 發現自動建立管理員的程式有缺陷時，沒有直接改原團隊的 `AdminAccountInitializer`，而是改用資料庫內建帳號，把問題記錄在本文件。
  - 前端本機的 `environment.ts` 修改（指向本機後端）在確認最終做法前沒有 commit；最終改為同源 `'/'` 搭配 `proxy.conf.json` 後才一起提交。
- **commit 前先編譯**：後端 `mvn compile` 與單元測試、前端 `ng build` 通過後才 commit，commit 依步驟分次提交，可以在 git 歷史看到開發過程。
- **推送前先問**：推送到 GitHub 屬於對外動作，每次都先取得同意。

## 5. 我搞懂了什麼

### 5.1 批次停用：每位使用者各自一個交易（新寫）
位置：[AdminService.java](src/main/java/com/example/demo/Service/AdminService.java) 的 `batchChangeUserAccountStatus` 與 `changeOneUserStatus`

這段程式處理批次停用功能，須滿足以下條件：批次處理時每一筆都要是單獨的交易，失敗會單獨回滾並把原因放進回傳結果給畫面顯示，成功可以在管理員操作紀錄中找到紀錄。

先用`if`擋掉沒選user、沒寫原因、原因超過字數的情況，再進行後續處理。用`LinkedHashSet`去除重複id，並保留使用者選擇的順序，並以`TransactionTemplate`取代 `@Transactional`，讓每一筆可以單獨處理，並用`try catch`來抓預期的失敗（有人名與原因）和預期外的錯誤，避免一筆失敗就讓整批中斷，分別將成功與失敗的情況加入對應的`ArrayList`方便畫面顯示，最後用`Dto`回傳。

### 5.2 條件式更新 `updateStatusIfCurrent`（原有）
位置：[UserRepo.java](src/main/java/com/example/demo/Repository/UserRepo.java)

這段用來更新使用者帳號狀態，使用和其他Repo相同的`@Query`的寫法。為了避免兩人同時操作時，狀態在檢查後被別人改掉，把檢查和修改放同一句 SQL，只有狀態符合預期的時候才進行修改，回傳影響筆數方便後續使用時可以檢查成功與否。

### 5.3 資料庫初始化腳本 `db-init.sh`（部署）
位置：[deploy/db-init.sh](deploy/db-init.sh)

- 這個腳本是在VM中由`docker-compose.yml`呼叫，在SQL Server啟動後執行，將資料庫初始化後，再啟動 api，最後啟動 web。
- 因為每次`docker compose up -d`都會執行，所以設定只有資料庫不存在時才執行建庫腳本，如果已經有就跳過，避免腳本`DROP`掉資料庫清掉資料，並設定結束碼為0。
- 使用`set -e`設定任何指令失敗就停止腳本，並搭配 `-b` 讓SQL出錯時回傳非0的結束碼來讓`set -e`偵測。
- 在本機用 Docker 試建時失敗，請AI查看錯誤後發現因為`sqlcmd`預設關閉`QUOTED_IDENTIFIER`，所以建立篩選索引失敗，補上 `-I`，並用`trap cleanup_on_failure ERR`來清除建到一半的資料庫，避免下一次部署時誤以為初始化已經完成。
- 檢查資料庫不存在後，執行建庫的SQL腳本，成功結束碼為0，啟動`api`，失敗觸發`trap cleanup_on_failure ERR`，結束碼非0，不啟動`api`。
