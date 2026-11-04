#!/bin/bash
# 資料庫不存在時才執行建庫腳本；已存在就跳過，避免 MarketDayDB.sql 的 DROP DATABASE 清掉資料。
# -I 開啟 QUOTED_IDENTIFIER（建立索引所需，sqlcmd 預設為關閉）。
set -e
SQLCMD="/opt/mssql-tools/bin/sqlcmd -S db -U sa -P $DB_PASSWORD -b -I"

EXISTS=$($SQLCMD -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.databases WHERE name = N'MarketDayDB'" | tr -d '[:space:]')
if [ "$EXISTS" = "1" ]; then
  echo "[db-init] MarketDayDB 已存在，略過初始化。"
  exit 0
fi

# 中途失敗時移除建到一半的資料庫，否則下次啟動會誤以為已初始化完成
cleanup_on_failure() {
  echo "[db-init] 初始化失敗，移除未完成的 MarketDayDB。" >&2
  $SQLCMD -Q "IF DB_ID(N'MarketDayDB') IS NOT NULL BEGIN ALTER DATABASE MarketDayDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE MarketDayDB; END" || true
}
trap cleanup_on_failure ERR

echo "[db-init] 建立 MarketDayDB ..."
$SQLCMD -i /sql/MarketDayDB.sql
echo "[db-init] 寫入分類種子資料 ..."
$SQLCMD -i /sql/categories.sql
echo "[db-init] 完成。"
