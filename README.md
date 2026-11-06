# Market Day API

> 作業說明（外部網址、部署過程、AI 規則、批次停用／恢復功能）請見 [README-作業.md](README-作業.md)。

Market Day 是小集日市集平台的 Spring Boot API 專案，提供帳號登入註冊、攤主資料、主辦資料、活動查詢、攤位選位、主辦後台管理、設備統計與帳務匯出等功能。


## 更新紀錄

### 更新日誌編寫規則

- 更新日誌只記錄正式功能、API、資料庫結構、商業規則、錯誤修正及測試結果。
- `sql/test*.sql` 屬於開發及驗證用測試資料，不在更新日誌中記錄檔名、建立筆數、測試帳號或測試資料內容。
- 測試資料的使用方式與預期結果應直接寫在對應的 `test*.sql` 註解內，不重複放入 README 更新日誌。

### 2026-07-26

#### simon branch

- 主辦方基本資料 API 恢復只處理主辦資料；藍新設定改由獨立頁面使用 `/api/organizer/newebpay/portal`、`load`、`save`、`verify` 四支 API。
- portal API 提供藍新官方註冊及登入網址；load API 載入 MerchantID 與設定狀態，但 HashKey、HashIV 固定回傳空字串；save API 負責格式驗證及加密保存。
- verify API 會建立一筆獨立的 NT$1 藍新商店驗證付款；只有藍新 callback 簽章、MerchantID、訂單與金額皆驗證成功，帳戶才標記為 `VERIFIED`。
- 藍新帳戶儲存或更新後為 `DISABLED / UNVERIFIED`，開始 NT$1 驗證後為 `DISABLED / PENDING`；成功才切換為 `ACTIVE / VERIFIED`，失敗則為 `DISABLED / FAILED`。
- 建立正式付款、退款及活動綁定金流帳戶時皆再次檢查帳戶必須同時為 `ACTIVE + VERIFIED`，避免舊資料中的 `ACTIVE / PENDING` 帳戶被誤用。
- 保留 `GET /api/organizers/me/payment-account` 供後端或管理介面檢查目前綁定狀態。
- 建立活動時會綁定登入主辦方的有效藍新帳戶；攤主建立付款時，MerchantID、TradeInfo 與 TradeSha 均使用該活動主辦方的帳戶產生，款項不再流入共用商店。
- 藍新 Notify 與 Return 會依回傳 MerchantID 找到對應主辦方帳戶，使用該帳戶的 HashKey、HashIV 驗證及解密，再比對付款單綁定帳戶後更新付款與報名狀態。
- 交易查詢與信用卡 Close API 改由原付款單的 `payment_account_id` 取得憑證；退款不使用活動目前帳戶或全域預設帳戶，確保由原收款主辦方商店退回原信用卡交易。
- 主辦方確認退款前會檢查活動擁有者與原付款帳戶擁有者一致；藍新退款成功回應亦會驗證 MerchantID、商店訂單編號、交易序號及金額。
- 已請款交易使用藍新退款流程；僅授權、尚未請款的交易則調整最終請款金額，兩者成功後皆會完成系統退款狀態及通知更新。
- 主辦方藍新帳戶一旦已有付款紀錄即禁止更換 MerchantID，避免歷史交易無法查詢或退款；相同 MerchantID 仍可更新金鑰。
- `NEWEBPAY_NOTIFY_URL`、`NEWEBPAY_RETURN_URL` 保留為藍新可公開存取的後端端點，`FRONTEND_URL` 僅用於 Return 驗證完成後導回前端付款結果頁。
- 更新 Spring Boot 冒煙測試設定，改用主辦方金流憑證加密主金鑰及目前的 Gateway、Query、Close、Notify、Return、Frontend 設定；啟動後會由 OpenAPI 確認四支主辦方藍新 API 與 Notify、Return callback 均已註冊。
- 更新 SQL Server 整合測試資料與預期：品牌探索測試補上已核准且未取消的已發布活動參與紀錄，主辦活動管理測試保留已取消活動，自動選位後的品牌公開判斷統一使用台北時間；重建 `MarketDayDB_Test` 後 35 項整合測試全部通過。

### 2026-07-22

#### yingtung branch

- 新增 `BRANDS_PUBLISHED` 活動狀態，明確區分「最終名單確認中」與「品牌已公開」；`FINAL_REVIEW` 會依 `brands_public_at`、活動起訖時間動態轉換顯示狀態。
- 完善主辦方與系統管理員的活動狀態篩選，並修正活動結束、選位截止的精確時間邊界判斷。
- 管理員退回下架申請時，會依品牌是否已公開恢復為 `PUBLISHED` 或 `FINAL_REVIEW`，避免覆蓋原本的品牌公開進度。
- 新增自動選位完整流程整合測試，涵蓋取消未付款報名、已付款報名自動分配不同攤位、品牌與地圖公開、攤位不足及重複執行的冪等性。
- 主辦方攤位、設備與帳務管理查詢新增 `brandsPublicAt`，並依報名、品牌公開與活動時間顯示已發布、最終名單確認中、品牌已公開、進行中或已結束。
- 修正活動日期缺漏或結束日早於開始日時，SQL Server 生成活動日期可能出現 `TOP` 負值的錯誤。
- 請求紀錄寫入失敗時，WARN 只輸出請求與錯誤摘要，完整 Stack Trace 改放 DEBUG，避免非主要的記錄錯誤淹沒實際 API 問題。
- 同步更新管理員活動審核、補件、地圖完成及下架通知的測試文案，不再在使用者通知內顯示活動 ID 或申請 ID。

### 2026-07-21

#### maisy branch

- 攤主首頁初始化 API `GET /api/vendor/dashboard/init` 新增 `pendingRefundCount`，回傳目前登入攤主處於退款申請中或退款處理中的報名筆數。
- 退款處理數量依 `refunds.refund_status IN (REFUND_REQUESTED, REFUNDING)` 統計，並以報名單去重，避免同一筆報名重複計數；尚未建立攤主資料時回傳 `0`。
- 同步更新攤主首頁 Response、Service、Repository 查詢及單元測試；`VendorDashboardServiceTest` 共 3 項測試通過。

#### simon branch

- 攤主人工選位與報名截止統一使用 `registration_end_at` 的完整日期時間判斷；到達前端設定的確切截止時間後即停止報名與人工選位，不再只比較日期。
- 自動配位排程改為台北時區每分鐘整點執行，僅查詢 `workflow_status = PUBLISHED` 且 `registration_end_at < 現在時間` 的活動，使截止後的處理偏差維持在一分鐘內。
- 自動程序會取消未付款申請，替已付款但尚未選位的申請分配可用攤位；全部符合資格的申請完成選位後，將活動更新為 `FINAL_REVIEW`，並以 `COALESCE` 寫入 `brands_public_at`。
- 公開活動地圖、攤位及品牌活動資訊統一要求 `brands_public_at IS NOT NULL AND brands_public_at <= SYSDATETIME()`；尚未寫入公開時間或公開時間尚未到達時不回傳品牌公開資料。
- 修正活動狀態文字：`brands_public_at` 為空時顯示「最終名單確認中」，已寫入且公開後顯示「品牌已公開」。
- 更新自動配位排程測試，並通過自動配位、公開活動、品牌、攤位及 Controller 相關 34 項測試。

### 2026-07-20

#### yingtung branch

- 主辦方帳務活動與設備管理活動資料新增 `coverImageUrl`，讓前端列表及詳情頁可顯示活動封面。
- 主辦方帳務付款明細新增 `applicationId`，供前端正確識別報名資料並導向付款／退款詳情。
- 主辦方報名詳情補上活動封面，以及品牌 `brandId`、`avatarImageUrl`，供前端顯示品牌圖片並開啟正確的品牌詳情。
- 修正主辦方報名審核 API 的 OpenAPI 中文說明，明確區分「審核通過攤主報名」與「審核未通過攤主報名」。

### 2026-07-19

#### yingtung branch

- 新增主辦方活動撤回審核 API：`POST /api/organizer/events/{eventId}/withdraw`，僅允許活動所屬主辦方將 `PENDING_REVIEW` 活動撤回為 `DRAFT`，撤回後可重新編輯及送審。
- 撤回審核會保留 `review_note` 與最後一次成功送審時間，並寫入 `request_logs`、`status_logs`，完整保存送審與撤回歷程。
- 活動設備租借、基本用電及額外用電狀態改由 `event_equipments` 明細即時計算，不再依賴 `market_events` 中不存在的設備布林欄位。
- 修正活動儲存時未填完整草稿欄位造成的 500 錯誤，並強化主辦方活動審核流程的條件更新與更新筆數檢查，避免狀態競態覆寫。
- 新增主辦方活動發布 API：`POST /api/organizer/events/{eventId}/publish`，僅允許活動所屬主辦方發布 `READY_TO_PUBLISH` 活動。
- 發布前會完整驗證活動資料、圖片、分區、日期與實際攤位地圖；`event_stalls` 數量必須等於 `max_booths`，報名截止、活動開始或活動結束後禁止首次發布。
- 發布成功後將狀態更新為 `PUBLISHED`，並以 `COALESCE` 記錄第一次 `public_info_at`，下架後重新發布不會覆蓋首次公開時間。
- 調整活動顯示狀態規則：報名開始前顯示「已發布」、報名期間依容量顯示「報名中／已額滿」、報名截止後至活動開始前顯示「最終確認」，活動期間及結束後分別顯示「進行中／已結束」。
- 修正管理員地圖建置完成流程的條件更新與更新筆數檢查，避免活動狀態已變更時仍被覆寫為 `READY_TO_PUBLISH`。
- 新增主辦方活動下架申請 API：`POST /api/organizer/events/{eventId}/unpublish-request`，下架原因必填且最多 500 字；成功後建立 `PENDING` 申請並將活動更新為 `UNPUBLISH_REQUESTED`。
- 下架申請審核期間活動仍維持公開；管理員核准後改為 `UNPUBLISHED`，拒絕後一律回到 `PUBLISHED`，舊申請保留並允許日後建立新的下架申請。
- 攤主報名詳情新增下架申請中與已下架提示；已下架活動會說明後續退款流程，既有報名、付款、選位及退款資料不受下架申請影響。
- 管理員完成地圖建置時，後端會依各分區 `stall_count` 自動建立 `event_stalls`，使用實際 `zone_id` 並產生 `A01`、`A02`、`B01` 等攤位編號；建立攤位與更新活動狀態在同一交易內完成。
- 活動分區名稱限制為 `A 區` 至 `Z 區`，最多 26 區且不可重複；分區攤位總數必須等於 `max_booths`，避免發布時發生地圖攤位數量不一致。
- 攤主市集列表與詳情只顯示目前報名期間內可報名或已額滿的活動，不再顯示尚未開放報名的活動，並統一使用「報名中／已額滿」狀態。
- 新增主辦方刪除草稿活動 API：`DELETE /api/organizer/events/{eventId}`，僅允許活動所屬主辦方刪除 `DRAFT` 活動；待審核撤回為草稿後也可刪除。
- 刪除活動採狀態保留方式，將 `workflow_status` 由 `DRAFT` 更新為 `CANCELLED`，不實體刪除活動、分類、設備、分區、圖片 URL 或圖片檔案，並保留所有送審、撤回及稽核紀錄。
- 已刪除的 `CANCELLED` 活動不再出現在主辦方活動列表與詳情；刪除使用資料列鎖與條件更新防止和送審同時執行，成功回傳 `eventId`、`eventTitle` 並寫入活動狀態紀錄。
- 補齊撤回、發布、下架申請、地圖攤位建立、分區驗證、活動刪除、狀態判斷與競態處理的 Service、Controller、狀態紀錄及 SQL Server 整合測試。
#### yushuan branch

- 新增主辦方退款確認 API：`POST /api/organizer/refunds/review`，主辦方可針對攤主已送出的退款申請進行第一次同意退款確認。
- `POST /api/organizer/refunds/review` 會驗證 Bearer Token、主辦方身分、退款單歸屬、原付款狀態、付款金流來源與藍新交易編號。
- 主辦方退款確認只允許處理 `refunds.refund_status = REFUND_REQUESTED` 的退款申請；通過後會先更新為 `REFUNDING`，再呼叫藍新信用卡請退款 API。
- 新增主辦方退款金流重試 API：`POST /api/organizer/refunds/payment`，僅允許 `REFUNDING` 或 `REFUND_FAILED` 狀態的退款單重試藍新退款金流。
- 藍新退款金流使用 `payments.payment_no` 作為 `MerchantOrderNo`，使用 `payments.provider_trade_no` 作為藍新 `TradeNo`，並以 `refunds.amount` 作為退款規則金額。
- 退款前會先查詢藍新交易狀態；若交易尚未請款完成，改以保留請款金額處理，確保保證金不退還，只退還扣除保證金後的金額。
- 藍新退款成功後更新 `refunds.refund_status = REFUNDED`、寫入 `refunded_at`，並清空 `failed_reason`。
- 藍新退款失敗後更新 `refunds.refund_status = REFUND_FAILED`，並將藍新錯誤訊息或例外原因寫入 `failed_reason`。
- 成功或失敗皆會寫入 `status_logs`，紀錄 `refunds.refund_status` 的退款處理狀態變化。
- 退款成功時通知攤主與主辦方；退款失敗時通知主辦方，以利後續人工確認或重試。
- 目前資料庫現有可保存退款狀態、退款成功時間、退款失敗原因、本地付款編號MerchantOrderNo、藍新交易序號

#### simon branch

- 新增主辦方付款列表 API：`POST /api/organizer/payments/search`，只列出已進入付款階段的報名，支援活動／品牌關鍵字、八種付款及退款狀態、付款日期區間與分頁查詢，並回傳攤主名稱及目前報名狀態。
- 新增主辦方付款詳情 API：`GET /api/organizer/payments/{id}`，以報名 ID 查詢活動、報名狀態時間軸、攤主、品牌、付款、報名費用、基本設備／用電、租借設備及額外用電資訊。
- 付款詳情僅在報名存在退款流程時回傳退款資訊及退款明細；退款金額不包含保證金，尚未進入退款流程時 `refund` 與 `refundDetails` 回傳 `null`。
- 付款詳情活動日期補上中文星期與 24 小時制活動時間；租借設備及額外用電回傳單價、租借天數與小計。
- 明確區分退款流程與報名取消狀態：退款申請中、退款處理中及退款失敗維持有效報名，只有退款完成後才將報名視同取消。
- 更新付款列表與付款詳情的 Controller、Service 及 SQL Server Repository 測試；本次驗證通過 294 項單元／Controller／Filter／Spring Boot 冒煙測試，以及重建 `MarketDayDB_Test` 後的 25 項 SQL Server 整合測試。
- 新增主辦方現金退還保證金 API：`POST /api/organizer/deposits/refund?applicationId={applicationId}`，只需提供報名 ID，後端會反查攤主、活動及主辦方所有權。
- 保證金退還屬現場行政現金流程，不建立 `refunds`、不呼叫藍新金流；成功時只將 `event_applications.deposit_status` 更新為 `RETURNED`，並寫入 `request_logs` 與 `status_logs`。
- 保證金退還會優先驗證目前時間是否介於活動 `start_at` 與 `end_at`；不在活動進行期間時直接拒絕，不再繼續判斷取消、退款、付款、選位或保證金狀態。
- 活動進行期間內，僅允許已審核通過、已付款、未取消、沒有退款流程、所有報名日期皆已選位、保證金大於 0 且尚未退還的報名執行退還；重複退還及條件不符回傳 HTTP `409`。
- 修正未攜帶 JWT 的異動 API 無法寫入 `request_logs.user_id`：本地註冊、Email 驗證、重寄註冊驗證碼、申請／完成密碼重設及 Google 註冊，會依 request body、成功回應或 reset token 反查使用者 ID。
- 登入 API 成功後改由登入回應中的使用者 Email 反查並寫入 `request_logs.user_id`；JWT API 仍優先使用 Authorization Token 識別使用者。
- 修正攤主登入 request log 查詢路徑拼字，由 `/api/vender/local-login`、`/api/vender/google-login` 改為實際路徑 `/api/vendor/local-login`、`/api/vendor/google-login`，讓後台最後登入時間與登入紀錄可正確統計。

### 2026-07-18

#### yingtung branch

- 新增主辦方活動列表 API：`GET /api/organizer/events/search`，支援活動名稱、工作流程狀態、活動期間重疊、分頁與活動管理排序查詢，並回傳報名、待審核、已付款及已選位等活動概況數量。
- 新增主辦方活動詳情 API：`GET /api/organizer/events/{eventId}`，回傳活動基本資料、日期、地點、交通方式、活動分類、攤位與分區、設備及用電設定、審核備註與目前可執行動作，供詳情及編輯頁共用。
- 新增主辦方活動儲存 API：`POST /api/organizer/events`，以相同 Request 格式支援建立及修改活動，並同步儲存多筆活動分類、攤位分區、設備與用電項目；活動圖片改由 `POST /api/images` 個別上傳及替換。
- 活動草稿允許尚未填完送審必填資料；未填攤位數時不再因 `maxBooths` 型別轉換發生儲存錯誤，無法儲存時補上對應中文 API 訊息。
- 主辦方活動可設定多個活動分類；攤主品牌仍維持單一品牌分類，並同步調整 Entity、Repository 與 SQL Server 整合測試結構。
- 新增活動送審／重新送審 API：`POST /api/organizer/events/{eventId}/submit-review`，僅允許 `DRAFT` 與 `REVISION_REQUIRED` 活動送出，成功後更新為 `PENDING_REVIEW`。
- 送審前完整驗證活動名稱、簡介、介紹、分類、圖片、未來日期、場地、三項交通資訊、攤位分區、費用、設備及用電設定；資料不完整時回傳 `missingFields`，狀態不允許或重複送審時回傳 409。
- 新增活動送審狀態紀錄，並修正 `submittedAt` 查詢只採用該活動最新一筆成功送審紀錄，避免不同活動或舊送審紀錄互相影響。
- 補上主辦方活動列表、詳情、儲存、初次送審、重新送審、資料缺漏與狀態紀錄等單元測試及 SQL Server Repository 整合測試。

### 2026-07-18

#### simon branch

- 活動分類維持多對多關聯，由 `market_event_categories` 保存；品牌分類改回多對一，由 `vendor_profiles.category_id` 保存單一分類。
- 攤主品牌儲存改回以 `categoryId` 接收單一分類；品牌與攤主相關 Response 改回傳單一 `category`，品牌分類篩選仍以單一分類名稱輸入。
- 分類相關列表採活動／品牌基本資料與分類集合分段查詢，避免 JOIN 多筆分類後產生重複活動或品牌。
- 公開活動 API `POST /api/markets/search` 改以 `categoryIds` 篩選並回傳 `categories` 陣列；`GET /api/markets/{id}` 同步改為回傳完整分類集合。
- 管理員 `GET /api/admin/events/{id}` 改為回傳活動 `categories` 陣列；`GET /api/admin/users/{id}` 的攤主品牌種類則連接單一品牌分類。
- 調整攤主市集列表卡片資料，補上報名剩餘天數及各活動日期剩餘攤位；移除對不存在的 `event_images`、`event_traffic_infos` 資料表查詢。
- 補齊攤主市集詳情所需的多分類、每日攤位、費用、設備、用電、主辦方及交通資料；攤主報名流程與報名須知不列入此 API 回傳。
- 新增主辦方通知中心 `GET /api/organizer/notices`，支援主辦方通知分類、未讀數、分頁及通知關聯資料，並串接既有報名與付款狀態異動。
- 公開活動搜尋移至 `AllController`；`POST /api/markets/search` 改用 Query Parameter，支援目前／歷史活動、關鍵字、日期、城市、單一中文活動狀態、多分類名稱及分頁，且只查詢 `PUBLISHED` 活動。
- 公開活動列表的活動狀態改回傳中文，活動起訖日期附帶星期（一至日）；分類查詢會命中活動擁有的任一分類。
- 公開活動詳情 `GET /api/markets/{id}` 移至 `AllController` 並移除 `MarketController`；依 `brands_public_at` 回傳公開狀態，品牌公開後可用 `date`、`stallNo` 查詢指定日期與攤位的單一品牌資料。
- 公開活動詳情不回傳 `brandsPublicAt`、分區清單、攤位 ID 或攤位狀態；品牌未公開時隱藏地圖及攤位品牌資訊。
- 公開活動詳情補上明確操作錯誤訊息，包含活動不存在或未公開、日期超出活動範圍，以及指定日期找不到攤位編號。
- 新增及調整分類、品牌、市集、通知相關 Repository／Service 測試與整合測試資料庫結構。
- 新增攤主取消報名 API：`POST /api/vendor/CancelApplication/{id}`；僅待審核或待付款（含付款失敗待重試）的本人報名可取消，取消只更新 `event_applications.is_cancelled`，不刪除報名及關聯資料。
- 取消成功回傳 HTTP `200` 並寫入 `request_logs` 與 `status_logs`；報名不存在回傳 `404`，狀態不可取消或重複取消回傳 `409`，失敗操作只寫入 `request_logs`。
- 新增主辦方現金退還保證金 API：`POST /api/organizer/deposits/refund`；以 `applicationId` 查找報名，只允許活動進行中、已付款且所有活動日期皆已選位的有效報名退還。
- 保證金退還屬行政現金流程，不建立金流退款；成功時只將 `event_applications.deposit_status` 更新為 `RETURNED`，並寫入 request/status log。

### 2026-07-17

#### yushuan branch

- 新增攤主退款申請 API：`POST /api/vendor/refunds`，攤主可針對已付款報名單送出退款申請。
- 後端會驗證 Bearer Token、攤主身分、報名單歸屬、審核狀態、付款狀態與是否已有退款紀錄。
- 退款金額依規則計算為 `payments.amount - event_applications.deposit_amount`，保證金不退還。
- 新增退款資料至 `refunds`，狀態為 `REFUND_REQUESTED`。
- 成功後寫入 `status_logs`，紀錄 `refunds.refund_status = REFUND_REQUESTED`。
- 成功後新增通知給主辦方，通知主辦方有新的退款申請待審核。
- 本 API 只處理「攤主提出退款申請」，不執行主辦方審核與藍新退款金流。

### 2026-07-16

#### yushuan branch

- 調整攤主藍新金流建立付款流程，`POST /api/vendor/payments/newebpay` 回傳資料補上 `applicationId`、`applicationNo`、`paymentId`、`merchantOrderNo`，讓前端能明確對應報名單、付款紀錄與藍新商店訂單編號。
- 調整付款狀態查詢 API，`GET /api/vendor/payments/{applicationNo}/status` 回傳資料補上 `applicationId`、`paymentId`、`merchantOrderNo`，並保留 `paymentNo`、`providerTradeNo`、`paymentRecordStatus`、`paidAt` 等付款結果欄位。
- 調整藍新付款完成導回流程，`POST /api/newebpay/return` redirect 至前端時補上 `applicationNo`，方便前端回到報名紀錄頁後重新查詢該筆報名付款狀態。
- 修正付款建立查詢只查金流必要欄位，不再為了聯絡人名稱查詢 `user_profiles.contact_name`，避免 `up.contact_name` 未 join 造成 SQL compile error。
- 保留藍新背景通知流程，`POST /api/newebpay/notify` 仍負責驗章、解密、比對金額，並更新 `payments.status` 與 `event_applications.payment_status`。
- 更新 `NewebPayServiceTest`，移除已不再使用的 `StallRepository` 測試注入，讓金流服務測試符合目前付款流程依賴。

#### simon branch

- 新增攤主首頁初始化 API：`GET /api/vendor/dashboard/init`，需攜帶有效的 Vendor Bearer Token。
- 初始化時依攤主必填資料完整度判斷 `needsProfile`；品牌、聯絡資訊、地址、分類、品牌頭像、品牌封面、品牌介紹及至少一筆商品任一缺漏時，回傳首次設定導引提示，由前端顯示固定導引內容。
- Instagram、Facebook、官方網站及商品圖片維持選填，不影響攤主資料完整度判斷。
- 攤主資料完整時，首頁回傳聯絡人名稱、待審核報名數、待付款報名數及待選位報名數；待付款包含 `PENDING`、`FAILED`，待選位為審核通過且付款完成但仍有參加日期尚未選位的報名。
- 攤主首頁回傳最近一年內最多 6 筆通知，依未讀優先、建立時間新到舊、通知 ID 大到小排序，不提供通知中心的分類與分頁參數。
- 修正新註冊攤主尚未建立 `vendor_profiles` 時呼叫 `GET /api/vendor/notices` 回傳「找不到攤主資料」的問題；現在會正常回傳未讀數 0、總筆數 0 與空通知陣列。
- Swagger 將原「攤主選位 API」合併至「攤主專區 API」，統一顯示攤主首頁、通知、報名、帳號與選位功能，既有 API 路徑不變。
- 新增攤主首頁、空通知與 Controller 委派測試，並通過完整單元測試及 Spring Boot 啟動冒煙測試。

### 2026-07-15

#### yingtung branch

- 新增主辦方後台初始化 API：`GET /api/organizer/dashboard/init`，主辦方登入後可取得 `needsProfile`，判斷是否需要先完成主辦方資料。
- 主辦方後台初始化會檢查主辦名稱、聯絡資訊、地址與服務時間等必填欄位；`companyName`、`taxId` 為選填欄位，不影響 `needsProfile` 判斷。
- 新增 `OrganizerDashboardInitResponse`，統一回傳主辦方資料是否需要補填。
- 將 `GET /api/organizer/dashboard/init` 加入 JWT 保護清單，需攜帶有效的 Bearer Token 才能呼叫。
- 新增主辦方後台初始化單元測試，涵蓋必填欄位缺漏、公司資料未填及資料完整等情境。
- `POST /api/organizer/profile/save` 的 `companyName`、`taxId` 改為選填；若有填寫，仍會驗證公司名稱長度與統一編號格式。
- 新增重新寄送註冊驗證碼 API：`POST /api/auth/createAccount/resend`，限尚未完成信箱驗證的本地帳號使用，重新寄送前會作廢舊驗證碼並建立 10 分鐘有效的新驗證碼。
- 新增 `ResendRegistrationVerificationRequest`，驗證重新寄送註冊驗證碼時的 Email 必填與格式。
- 建立 `user_profiles` 時同步儲存註冊名稱與聯絡 Email；尚未建立攤主或主辦 profile 時，使用 `user_profiles.contact_name` 作為帳號顯示名稱。
- 補充重新寄送驗證碼與本地帳號不存在的中文 API 訊息，並同步更新主辦方資料欄位文件。

#### simon branch

- 建立共用 `NotificationService`、通知 Repository、通知建立指令與分類／事件／關聯對象 enum，讓現有與後續 API 能以統一入口建立單人、多人或系統通知。
- 將攤主送出報名、主辦方審核通過／不通過、藍新付款成功／失敗與攤位選擇完成等狀態變更納入站內通知寫入流程。
- 新增攤主通知中心 API：`GET /api/vendor/notices`，限目前登入攤主查詢，支援中文篩選、分頁及未讀總數。
- 通知篩選值統一為「全部、未讀、報名審核、付款相關、攤位分配、活動異動」；結果依未讀優先、建立時間新到舊、通知 ID 大到小排序。
- 通知預設僅查詢最近一年，可透過 `notification.retention-years` 調整，且至少保留一年。
- 補上攤主通知 Controller、Service 與查詢規則測試，包含中文篩選值與錯誤篩選值驗證。
- 強化既有管理員登入端點 `POST /api/admin/local-login`：登入成功簽發包含 `role=ADMIN` 的 JWT，並在 Swagger 補上管理員帳密登入範例。
- 管理員登入以外的全部 `/api/admin/**` 端點皆須攜帶有效 Bearer Token；無效或逾期工作階段回傳 401，VENDOR／ORGANIZER token 回傳 403。
- `JwtAuthenticationFilter` 由逐支維護 `protectedApis` 改為「受保護路徑前綴＋明確公開端點白名單」；`/api/vendor/**`、`/api/organizer/**`、`/api/admin/**`、`/api/auth/**`、`/api/account/**`、`/api/images**` 與 `/api/stalls/**` 預設需要 JWT。
- 登入、註冊、信箱驗證、密碼重設、公開市集查詢與藍新回呼維持公開；所有 CORS `OPTIONS` 預檢請求亦直接放行。新增前綴保護、公開白名單、管理員角色及 CORS 測試。

最後更新：2026-07-19

## 更新紀錄

### 2026-07-14

- `GET /api/vendor/dashboard/init`：登入後判斷目前攤主是否需要填寫攤位資料；該使用者沒有 `vendor_profiles` 時回傳 `needsProfileSetup: true`。

#### simon branch

- 新增台灣地址下拉選單 API：`GET /api/addresses/cities` 回傳台灣縣市清單，`GET /api/addresses/districts?city={縣市}` 依所選縣市回傳所屬地區；資料由 `TaiwanAddressService` 提供，無效縣市會回傳驗證錯誤。
- 新增共用正式圖片儲存 API：`POST /api/images`，支援攤主大頭照、攤主封面、商品圖片、活動封面與活動地圖五種用途。
- 圖片 API 依用途使用 `productId` 或 `eventId` 綁定目標資料，並驗證 JWT、帳號角色及資料所有權；攤主大頭照與封面不需提供目標 ID。
- 圖片檔案儲存於可設定的 `images` 目錄，透過 `/images/**` 提供前端存取；新增圖片目錄、公開網址、5 MB 上傳限制與 CORS 設定。
- 圖片上傳會檢查實際檔案內容；一般圖片僅接受 JPG、PNG，活動地圖另接受 PDF，儲存或資料綁定失敗時不會更新圖片 URL。
- `POST /api/vendor/stall/save` 改為一次接收品牌資料與完整商品清單，商品最多 3 筆；既有商品依 ID 更新、新商品新增、未送出的既有商品刪除。
- 攤主品牌資料儲存不再修改大頭照與封面 URL，避免未重新上傳圖片時將原圖片覆蓋為空值；圖片統一由 `POST /api/images` 更新。
- 移除獨立的攤主商品新增、編輯及刪除 API：`/api/vendor/stall/addproduct`、`/api/vendor/stall/edituct/{id}`、`/api/vendor/stall/deleteproduct/{id}`。
- `GET /api/brands/search` 與 `GET /api/brands/{id}` 改為回傳品牌目前刊登的全部商品，不再限制特色商品、商品狀態或最多 3 筆。
- 補上圖片上傳大小超限及圖片／商品儲存驗證的統一錯誤回應，並同步更新 Swagger 說明。

### 2026-07-11

#### simon branch

- 新增品牌展示 API：`GET /api/brands/search`、`GET /api/brands/{id}`、`GET /api/brands/scroll-options`，可用於前台品牌列表、品牌詳情與篩選選項。
- 新增主辦 profile API：`GET /api/organizer/profile/load`、`POST /api/organizer/profile/save`。
- 主辦 profile 的服務時間改為分開輸入與輸出：`serviceStartTime`、`serviceEndTime`。
- 主辦 profile 的地址輸出只回傳原本儲存的 `address`，不再自動組合 `city + district + address`。
- 新增 `TaiwanAddressService`，用來驗證台灣縣市與行政區。
- `POST /api/organizer/profile/save` 增加欄位輸入限制與對應錯誤訊息。
- `GET /api/organizer/equipment/{eventId}` 新增 `equipmentOverview`，包含報名攤數、基本設備數、基本用電數、設備租借數、額外用電數、車牌登記數。
- `GET /api/organizer/stalls/search` 新增 `availableStallCount`、`selectedStallCount`。
- 新增攤主資料 API：`GET /api/vendor/stall/load`、`POST /api/vendor/stall/save`、`POST /api/vendor/stall/addproduct`、`POST /api/vendor/stall/edituct/{id}`、`POST /api/vendor/stall/deleteproduct/{id}`。
- `GET /api/organizer/stall/{eventId}` 的 `event` 區塊新增 `selectedStallCount`、`availableStallCount`，可顯示攤位總數、已選擇攤位數與可選攤位數。
- `GET /api/organizer/stall/{eventId}` 的 `keyword` 會查詢攤位編號 `stallNo` 與已選攤位的品牌名稱 `selectedVendor.name`。
- `GET /api/organizer/stall/{eventId}/{stallNo}` 的攤位詳情 `vendor` 區塊新增 `brandType`。
- 移除 `GET /api/organizer/account`，主辦個人資料改由 profile API 讀取與儲存。
- 同步更新 Swagger 與 API response 文件。

### 2026-07-10

#### yushuan branch

- 新增藍新金流付款流程 API，提供攤主於報名審核通過後建立付款、前往藍新付款頁、接收藍新付款通知、查詢本地付款狀態與補查藍新交易狀態等功能。
- `POST /api/vendor/payments/newebpay` 新增攤主建立藍新付款 API。前端點擊付款按鈕後呼叫此 API，後端會檢查 JWT token、攤主身分、申請單是否存在、申請單是否屬於目前攤主、審核狀態是否通過、付款狀態是否可付款，以及付款金額是否有效；檢查通過後建立本地付款資料並回傳藍新付款表單所需欄位。
- `POST /api/newebpay/notify` 新增藍新背景付款通知 API。藍新付款完成後會呼叫此 API，後端負責驗證 TradeSha、解密 TradeInfo、比對付款金額，並於付款成功時更新本地付款狀態為 `PAID`，同步更新申請單付款狀態。
- `POST /api/newebpay/return` 新增藍新付款完成返回 API。使用者完成藍新付款後會被導回此 API，後端接收付款返回資料後導回前端付款結果頁；付款成功狀態仍以 `POST /api/newebpay/notify` 為主要依據。
- `GET /api/newebpay/return` 新增付款返回備用/測試入口，供瀏覽器直接開啟 ReturnURL 時導回前端付款結果頁。
- `GET /api/vendor/payments/{applicationNo}/status` 新增攤主查詢本地付款狀態 API。前端可依申請單號查詢目前本地付款狀態，用於付款頁面刷新、付款結果頁確認或付款按鈕狀態判斷。
- `POST /api/vendor/payments/{applicationNo}/newebpay-query` 新增藍新交易狀態補查 API。此 API 用於向藍新查詢指定申請單對應交易狀態，通常用於除錯、付款通知未收到或需要補正本地付款狀態時使用。
- 新增 `NewebPayProperties` 讀取藍新金流設定，包含商店代號、HashKey、HashIV、版本、付款網址、NotifyURL、ReturnURL 與查詢網址等設定。
- 新增 `NewebPayService` 處理藍新金流流程，包含建立付款資料、TradeInfo 加密、TradeSha 產生、Notify/Return 解密驗章、付款狀態更新與交易狀態補查。
- 新增 `PaymentRepository` 操作金流相關資料，包含查詢申請單付款資料、建立付款紀錄、更新付款狀態與查詢付款狀態。
- 新增金流相關 DTO：`VendorPaymentRequest`、`NewebPayPaymentResponse`、`NewebPayQueryResponse`、`PaymentStatusResponse`。

### 2026-07-09

#### simon branch

- `GET /api/organizer/equipment/search` 新增主辦設備列表查詢。
- `GET /api/organizer/equipment/{eventId}` 新增設備租借管理、額外用電管理、車牌管理與統計資料。
- `GET /api/organizer/equipment/{eventId}/export` 新增設備資料 Excel 匯出。
- 主辦列表 API 支援 `page`、`pageSize` 分頁參數。
- `GET /api/organizer/accounts/{eventId}` 的帳務列表支援 `paymentPage`、`paymentPageSize`。

### 2026-07-07

#### simon branch

- `GET /api/organizer/applications/{id}` 新增活動、報名、費用、設備、狀態時間軸等詳細資料。
- 主辦攤位地圖 API 改為 `GET /api/organizer/stall/{eventId}`。
- 主辦攤位詳情 API 改為 `GET /api/organizer/stall/{eventId}/{stallNo}`。
- `GET /api/organizer/stall/{eventId}` 支援 `applyDate`、`keyword`、`status` 查詢。
- `GET /api/organizer/accounts/search` 新增主辦帳務活動列表查詢。
- `GET /api/organizer/accounts/{eventId}` 新增帳務明細。
- `GET /api/organizer/accounts/{eventId}/export` 新增帳務 Excel 匯出。

### 2026-07-02

#### simon branch

- `POST /api/stalls/select` 改為使用 `applicationNo` 與 `selections` 選位。
- `GET /api/vendor/stall-map/{applicationNo}` 新增 `applyDate` 查詢與多日期選位資訊。
- 新增公開攤位狀態 API：`GET /api/eventsMap/{eventId}/stallsStatus`。

### 2026-06-29

#### simon branch

- 註冊、登入與 Google OAuth 流程調整。
- `POST /api/auth/google-bind` 支援本地帳號綁定 Google。
- `GET /api/auth/me` 新增目前登入使用者資料。
- `POST /api/account/deactivate` 可停用目前帳號。
- `users` 新增 `name`、`phone`，並同步調整 profile 資料。
- `market_events` 時間欄位改為 `DATETIME2(0)`。

## 技術

- Java 21
- Spring Boot 3.5.14
- Spring Web
- Spring Data JDBC
- SQL Server
- Bean Validation
- JWT：jjwt 0.12.6
- Swagger / OpenAPI：springdoc-openapi 2.8.17
- Maven Wrapper
- Apache POI

## 專案結構

```text
demo/
├─ pom.xml
├─ mvnw / mvnw.cmd
├─ run-local.cmd
├─ README.md
├─ api-response.md
├─ swagger.md
└─ src/main/
   ├─ java/com/example/demo/
   │  ├─ Config/
   │  ├─ Controller/
   │  ├─ Filter/
   │  ├─ Repository/
   │  ├─ Service/
   │  ├─ dto/
   │  └─ swagger/
   └─ resources/
      ├─ application.properties
      └─ static/
```

## 環境變數

`run-local.cmd` 會設定本機開發常用環境變數並啟動 Spring Boot。

| 變數                  | 說明                    |
| --------------------- | ----------------------- |
| `DB_URL`            | SQL Server JDBC URL     |
| `DB_USERNAME`       | SQL Server 帳號         |
| `DB_PASSWORD`       | SQL Server 密碼         |
| `GOOGLE_CLIENT_ID`  | Google OAuth Client ID  |
| `MAIL_USERNAME`     | 寄信帳號                |
| `MAIL_PASSWORD`     | 寄信密碼或 App Password |
| `JWT_SECRET`        | JWT secret              |
| `JWT_EXPIRATION_MS` | JWT 有效時間            |
| `ADMIN_EMAIL`       | 預設管理員 email        |
| `ADMIN_PASSWORD`    | 預設管理員密碼          |

## 啟動與測試

啟動：

```powershell
cd demo
.\mvnw.cmd spring-boot:run
```

測試：

```powershell
cd demo
.\mvnw.cmd test
```

Push 前不可只執行上述一般測試；還需要驗證 SQL Server schema 與 Repository 查詢。完整測試請執行：

```cmd
src\test\run-test.cmd
```

一般測試與 SQL Server 整合測試兩個階段都必須顯示 `BUILD SUCCESS`。完整準備方式、失敗排查與 push 前檢查清單請參考 [Push 前測試流程](all-md/pre-push-testing.md)。

Swagger UI：

```text
http://localhost:8081/swagger-ui/marketDay/index.html
```

OpenAPI JSON：

```text
http://localhost:8081/v3/api-docs
```

## API Response 格式

所有 Controller 預設回傳 `ApiResponse<T>`。

成功範例：

```json
{
  "statusCode": 200,
  "message": "Organizer profile loaded successfully",
  "messageDetails": null,
  "data": {}
}
```

失敗範例：

```json
{
  "statusCode": 400,
  "message": "Validation failed",
  "messageDetails": "city is invalid",
  "data": null
}
```

## JWT 保護

需要登入的 API 必須帶：

```text
Authorization: Bearer {token}
```

受 JWT 保護的主要 API：

- `POST /api/auth/logout`
- `POST /api/auth/google-bind`
- `GET /api/auth/me`
- `POST /api/account/deactivate`
- `GET /api/vendor/account`
- `GET /api/vendor/notices`
- `GET /api/vendor/stall/load`
- `POST /api/vendor/stall/save`
- `GET /api/vendor/stall-map/{applicationNo}`
- `POST /api/stalls/select`
- `GET /api/organizer/profile/load`
- `POST /api/organizer/profile/save`
- `GET /api/organizer/accounts/{eventId}`
- `GET /api/organizer/accounts/{eventId}/export`
- `GET /api/organizer/stalls/search`
- `GET /api/organizer/equipment/search`
- `GET /api/organizer/equipment/{eventId}`
- `GET /api/organizer/equipment/{eventId}/export`
- `GET /api/organizer/stall/{eventId}`
- `GET /api/organizer/stall/{eventId}/{stallNo}`

## 公開 API

| Method | API                                       | 說明             |
| ------ | ----------------------------------------- | ---------------- |
| GET    | `/api/eventsMap/{eventId}/stallsStatus` | 公開攤位狀態地圖 |
| GET    | `/api/brands/scroll-options`            | 品牌列表篩選選項 |
| GET    | `/api/brands/search`                    | 品牌列表查詢     |
| GET    | `/api/brands/{id}`                      | 品牌詳情         |
| POST   | `/api/markets/search`                   | 市集列表查詢     |
| GET    | `/api/markets/{id}`                     | 市集詳情         |

## 帳號 API

| Method | API                                     | 說明              |
| ------ | --------------------------------------- | ----------------- |
| POST   | `/api/vendor/local-register`          | 攤主本地註冊      |
| POST   | `/api/organizer/local-register`       | 主辦本地註冊      |
| POST   | `/api/vendor/google-register`         | 攤主 Google 註冊  |
| POST   | `/api/organizer/google-register`      | 主辦 Google 註冊  |
| POST   | `/api/vendor/local-login`             | 攤主本地登入      |
| POST   | `/api/organizer/local-login`          | 主辦本地登入      |
| POST   | `/api/admin/local-login`              | 管理員登入        |
| POST   | `/api/vendor/google-login`            | 攤主 Google 登入  |
| POST   | `/api/organizer/google-login`         | 主辦 Google 登入  |
| POST   | `/api/auth/google-bind`               | 登入後綁定 Google |
| POST   | `/api/auth/createAccount/emailVerify` | 註冊信箱驗證      |
| POST   | `/api/auth/resetPassword/request`     | 申請重設密碼      |
| POST   | `/api/auth/resetPassword/emailVerify` | 重設密碼信箱驗證  |
| POST   | `/api/auth/resetPassword/reset`       | 重設密碼          |
| POST   | `/api/auth/logout`                    | 登出              |
| GET    | `/api/auth/me`                        | 目前登入使用者    |
| POST   | `/api/account/deactivate`             | 停用目前帳號      |

## 攤主金流 API

| Method | API                                                     | Request DTO              | JWT | 說明                                                             |
| ------ | ------------------------------------------------------- | ------------------------ | --- | ---------------------------------------------------------------- |
| POST   | `/api/vendor/payments/newebpay`                       | `VendorPaymentRequest` | 是  | 攤主建立藍新付款資料，後端檢查申請單狀態並回傳藍新付款表單欄位。 |
| GET    | `/api/vendor/payments/{applicationNo}/status`         | -                        | 是  | 查詢指定申請單的本地付款狀態。                                   |
| POST   | `/api/vendor/payments/{applicationNo}/newebpay-query` | -                        | 是  | 向藍新補查交易狀態，用於除錯或 Notify 未收到時補正狀態。         |

## 藍新金流回呼 API

| Method | API                      | Request          | JWT | 說明                                                           |
| ------ | ------------------------ | ---------------- | --- | -------------------------------------------------------------- |
| POST   | `/api/newebpay/notify` | 藍新回傳表單資料 | 否  | 藍新背景通知付款結果，後端驗章、解密、比對金額並更新付款狀態。 |
| POST   | `/api/newebpay/return` | 藍新回傳表單資料 | 否  | 使用者付款完成後由藍新導回，後端處理後導回前端付款結果頁。     |
| GET    | `/api/newebpay/return` | Query params     | 否  | ReturnURL 備用/測試入口，供瀏覽器直接開啟時導回前端。          |

## 藍新付款流程

```text
前端點付款按鈕
  ↓
POST /api/vendor/payments/newebpay
  ↓
後端檢查 token、攤主身分、申請單、歸屬、審核狀態、付款狀態與金額
  ↓
後端建立本地付款紀錄並回傳藍新付款表單資料
  ↓
前端 form POST 到藍新付款頁
  ↓
藍新付款完成
  ↓
POST /api/newebpay/notify
  ↓
後端驗章、解密、比對金額，更新本地付款狀態為 PAID
  ↓
POST /api/newebpay/return
  ↓
藍新導使用者回系統，後端再導回前端付款結果頁
```

## 攤主 API

| Method | API                                       | 說明                   |
| ------ | ----------------------------------------- | ---------------------- |
| GET    | `/api/vendor/account`                   | 攤主帳號資料           |
| GET    | `/api/vendor/notices`                   | 攤主通知中心篩選與分頁查詢 |
| GET    | `/api/vendor/stall/load`                | 讀取攤主品牌與商品資料 |
| POST   | `/api/vendor/stall/save`                | 儲存攤主品牌基本資料   |
| POST   | `/api/vendor/CancelApplication/{id}`    | 取消待審核或待付款報名 |
| GET    | `/api/vendor/stall-map/{applicationNo}` | 攤主選位地圖           |
| POST   | `/api/stalls/select`                    | 攤主送出選位           |

### 攤主取消報名

`POST /api/vendor/CancelApplication/{id}` 需要 Vendor Bearer Token，`id` 為報名單 ID，且報名單必須屬於目前登入攤主。

允許取消的狀態：

- `review_status = PENDING`：報名仍在待審核。
- `review_status = APPROVED` 且 `payment_status = PENDING`：已通過審核但仍待付款。
- `review_status = APPROVED` 且 `payment_status = FAILED`：付款失敗，仍可取消。

取消只會將 `event_applications.is_cancelled` 更新為 `true`，不會刪除報名單、報名日期、付款或其他關聯資料。已付款、已退件或已取消的報名不可取消。

| 情境 | HTTP 狀態 | 紀錄行為 |
| ---- | --------- | -------- |
| 首次成功取消 | `200` | 寫入 `request_logs`，並在 `status_logs` 紀錄 `event_applications.is_cancelled = true` |
| 報名單不存在或不屬於目前攤主 | `404` | 只寫入 `request_logs` |
| 報名狀態不可取消 | `409` | 只寫入 `request_logs` |
| 重複取消 | `409` | 只寫入 `request_logs` |

### 攤主通知中心

`GET /api/vendor/notices` 需要 Bearer Token，且帳號必須為已建立攤主資料的 `VENDOR`。

| Query 參數 | 預設值 | 說明 |
| ---------- | ------ | ---- |
| `filter` | `全部` | 僅接受：`全部`、`未讀`、`報名審核`、`付款相關`、`攤位分配`、`活動異動` |
| `page` | `1` | 頁碼從 1 開始 |
| `pageSize` | `10` | 每頁筆數，最大 10 |

- 只回傳目前登入攤主的通知，包含已讀與未讀。
- 預設限制為最近一年，可以 `notification.retention-years` 調整。
- 排序為未讀優先；相同閱讀狀態依 `createdAt` 由新到舊，相同時間再依 `id` 由大到小。
- `unreadCount` 為該攤主在保留期間內的全部未讀總數，不受 `filter` 分類限制。

### 攤主資料欄位

`GET /api/vendor/stall/load` 與 `POST /api/vendor/stall/save` 主要處理：

- 品牌名稱
- 負責人
- 聯絡電話
- 聯絡電子郵件
- 縣市
- 地區
- 地址
- Instagram
- Facebook
- 官方網站
- 品牌大頭照
- 品牌封面照
- 品牌簡介
- 品牌介紹
- 品牌類型
- 品牌商品

商品欄位：

- 商品圖片
- 商品名稱
- 商品簡述
- 商品金額

`POST /api/vendor/stall/save` 會一次處理完整商品清單：依 ID 更新既有商品、新增無 ID 商品，並刪除未再送出的既有商品。

## 主辦 API

| Method | API                                           | 說明             |
| ------ | --------------------------------------------- | ---------------- |
| GET    | `/api/organizer/profile/load`               | 讀取主辦基本資料 |
| POST   | `/api/organizer/profile/save`               | 儲存主辦基本資料 |
| GET    | `/api/organizer/events/search`              | 主辦活動列表查詢 |
| GET    | `/api/organizer/events/{eventId}`           | 主辦活動詳情     |
| POST   | `/api/organizer/events`                     | 建立或修改活動   |
| POST   | `/api/organizer/events/{eventId}/submit-review` | 初次送審或重新送審 |
| GET    | `/api/organizer/applications/search`        | 報名列表查詢     |
| GET    | `/api/organizer/applications/{id}`          | 報名詳情         |
| POST   | `/api/organizer/applications/{id}/approve`  | 審核通過         |
| POST   | `/api/organizer/applications/{id}/reject`   | 退回或拒絕       |
| POST   | `/api/organizer/deposits/refund`            | 現金退還保證金   |
| GET    | `/api/organizer/stalls/search`              | 攤位管理活動列表 |
| GET    | `/api/organizer/stall/{eventId}`            | 主辦攤位地圖     |
| GET    | `/api/organizer/stall/{eventId}/{stallNo}`  | 主辦攤位詳情     |
| GET    | `/api/organizer/equipment/search`           | 設備管理活動列表 |
| GET    | `/api/organizer/equipment/{eventId}`        | 設備管理詳情     |
| GET    | `/api/organizer/equipment/{eventId}/export` | 設備資料匯出     |
| GET    | `/api/organizer/accounts/search`            | 帳務管理活動列表 |
| GET    | `/api/organizer/accounts/{eventId}`         | 帳務管理詳情     |
| GET    | `/api/organizer/accounts/{eventId}/export`  | 帳務資料匯出     |

### 主辦方退還保證金

`POST /api/organizer/deposits/refund` 需要 Organizer Bearer Token，並使用 Query Parameter 傳入：

| 參數 | 必填 | 說明 |
| ---- | ---- | ---- |
| `applicationId` | 是 | 報名單 ID；後端會反查活動與攤主，並驗證活動屬於目前登入主辦方 |

退還條件：

- 報名狀態為 `APPROVED`，付款狀態為 `PAID`，且報名未取消。
- 報名不得存在退款申請、退款處理中、退款失敗或已退款紀錄。
- 每一個 `application_dates` 日期皆已綁定 `selected_stall_id`。
- 伺服器目前時間位於活動 `start_at` 至 `end_at` 之間。
- `deposit_amount` 大於 0，且 `deposit_status = NOT_RETURNED`。

驗證時會優先判斷活動期間；只要目前不在 `start_at` 至 `end_at` 之間，就直接回傳活動尚未進行或已結束的錯誤，不再繼續判斷取消、退款、付款、選位及保證金狀態。

此功能代表主辦方已在現場以現金退還保證金，只會將 `event_applications.deposit_status` 更新為 `RETURNED`，不建立 `refunds`、不呼叫藍新金流，也不異動原付款紀錄。成功回傳 HTTP `200` 並寫入 `status_logs`；資料不存在回傳 `404`，條件不符或重複退還回傳 `409`。

### 主辦 profile 欄位

`GET /api/organizer/profile/load` 輸出與 `POST /api/organizer/profile/save` 輸入包含：

- `organizerName`
- `contactName`
- `contactPhone`
- `contactEmail`
- `city`
- `district`
- `address`
- `companyName`
- `taxId`
- `serviceDays`
- `serviceStartTime`
- `serviceEndTime`

時間欄位分開回傳，不合併成一個字串。地址欄位只回傳資料庫中的 `address`，不自動組合縣市與地區。

### 主辦 profile 輸入限制

| 欄位                 | 規則                                                  |
| -------------------- | ----------------------------------------------------- |
| `organizerName`    | 必填，長度不得超過資料庫欄位限制                      |
| `contactName`      | 必填                                                  |
| `contactPhone`     | 必填，需符合電話格式                                  |
| `contactEmail`     | 必填，需符合 email 格式                               |
| `city`             | 必填，需存在於`TaiwanAddressService` 的台灣縣市清單 |
| `district`         | 必填，需存在於指定縣市的行政區清單                    |
| `address`          | 必填                                                  |
| `companyName`      | 選填，若填寫不得超過 150 字                           |
| `taxId`            | 若填寫需符合統一編號格式                              |
| `serviceDays`      | 必填                                                  |
| `serviceStartTime` | 必填，時間格式                                        |
| `serviceEndTime`   | 必填，時間格式，需晚於開始時間                        |

## 主辦攤位地圖

`GET /api/organizer/stall/{eventId}`

Query params：

| 參數          | 說明                                                                 |
| ------------- | -------------------------------------------------------------------- |
| `applyDate` | 活動日期，不填則使用預設日期                                         |
| `keyword`   | 查詢攤位編號`stallNo` 或已選攤位的品牌名稱 `selectedVendor.name` |
| `status`    | 篩選攤位狀態                                                         |

回傳重點：

```json
{
  "event": {
    "eventId": 1,
    "title": "活動名稱",
    "locationName": "活動地點",
    "startAt": "2026-07-18T14:00:00",
    "endAt": "2026-07-19T18:00:00",
    "totalStallCount": 150,
    "selectedStallCount": 12,
    "availableStallCount": 138
  },
  "applyDates": [],
  "currentApplyDate": "2026-07-18",
  "zones": []
}
```

攤位資料中的 `status` 會回傳顯示用狀態，例如可選擇、已選擇、不可選。

`GET /api/organizer/stall/{eventId}/{stallNo}`

回傳單一攤位資料，包含攤位編號、區域、尺寸、狀態、選位時間與攤主品牌資訊。`vendor.brandType` 會回傳品牌類型。

## 主辦設備管理

`GET /api/organizer/equipment/{eventId}` 主要回傳：

- `event`
- `equipmentOverview`
- `eventEquipments`
- `equipmentRentalStatistics`
- `equipmentRentalManagement`
- `extraPowerManagement`
- `vehicleManagement`

`equipmentOverview` 包含：

- 報名攤數
- 基本設備數
- 基本用電數
- 設備租借數
- 額外用電數
- 車牌登記數

## 主辦帳務管理

`GET /api/organizer/accounts/{eventId}` 支援：

- `status`
- `paymentPage`
- `paymentPageSize`

回傳內容包含：

- `event`
- `summary`
- `statistics`
- `payments`

`GET /api/organizer/accounts/{eventId}/export` 會輸出 Excel：

```text
account-report-{eventId}.xlsx
```

## 文件

- Swagger 補充：`swagger.md`
- API response 補充：`api-response.md`
- SQL 文件：`../sql/README.md`

## 注意事項

- `GET /api/organizer/account` 已移除，請改用 `GET /api/organizer/profile/load`。
- 商品刪除目前使用 `POST /api/vendor/stall/deleteproduct/{id}`。
- 商品編輯 API 路徑目前維持既有拼字：`POST /api/vendor/stall/edituct/{id}`。
- README 不記錄測試資料腳本內容，正式 API 行為以 Controller、Service、Swagger 與本文件為準。

