#!/bin/bash
# 資料庫不存在時才執行建庫腳本；已存在就跳過，避免 MarketDayDB.sql 的 DROP DATABASE 清掉資料。
set -e
SQLCMD="/opt/mssql-tools/bin/sqlcmd -S db -U sa -P $DB_PASSWORD -b"

EXISTS=$($SQLCMD -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM sys.databases WHERE name = N'MarketDayDB'" | tr -d '[:space:]')
if [ "$EXISTS" = "1" ]; then
  echo "[db-init] MarketDayDB 已存在，略過初始化。"
  exit 0
fi

echo "[db-init] 建立 MarketDayDB ..."
$SQLCMD -i /sql/MarketDayDB.sql
echo "[db-init] 寫入分類種子資料 ..."
$SQLCMD -i /sql/categories.sql
echo "[db-init] 完成。"
