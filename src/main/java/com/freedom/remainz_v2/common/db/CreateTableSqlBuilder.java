package com.freedom.remainz_v2.common.db;

import java.util.List;
import java.util.Map;

import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;

/**
 * TBL_DEFのカラム定義から、CREATE TABLE文を生成するクラスです。
 *
 * <p>
 * SQLiteでの動作を前提としています。SQLiteのAUTOINCREMENTは
 * 「{@code <カラム> INTEGER PRIMARY KEY AUTOINCREMENT}」という記述が必須のため、
 * 主キーカラム（{@code KEY_DIV=PRI}）は型・NOT NULL・DEFAULTの指定を行わず、
 * このSQLite固有の書式で出力します（本プロジェクトの主キーは必ず単一カラムの
 * 自動採番サロゲートキーであるため、この単純化で問題ありません）。
 * </p>
 */
public class CreateTableSqlBuilder {

    /**
     * CREATE TABLE文を生成します。
     *
     * @param tableName  テーブル物理名
     * @param columnDefs TBL_DEF由来のカラム定義行のリスト（1行=1カラム、定義順）
     * @return CREATE TABLE文
     */
    public String build(String tableName, List<Map<String, String>> columnDefs) {

        if (columnDefs == null || columnDefs.isEmpty()) {
            throw new BusinessRuleViolationException("カラム定義が存在しません。tableName=" + tableName);
        }

        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        StringBuilder columnPart = new StringBuilder();
        for (Map<String, String> columnDef : columnDefs) {
            if (columnPart.length() > 0) {
                columnPart.append(",\n");
            }
            columnPart.append("    ").append(buildColumnDefinition(columnDef));
        }

        sql.append(columnPart).append("\n);");
        return sql.toString();
    }

    private String buildColumnDefinition(Map<String, String> columnDef) {

        String fieldName = columnDef.get("FIELD_NAME");
        String typeName = columnDef.get("TYPE_NAME");
        String allowNull = columnDef.get("ALLOW_NULL");
        String keyDiv = columnDef.get("KEY_DIV");
        String defaultValue = columnDef.get("DEFAULT_VALUE");

        // 主キー(サロゲートキー)は、SQLiteのAUTOINCREMENTが要求する記述に固定する
        if ("PRI".equals(keyDiv)) {
            return fieldName + " INTEGER PRIMARY KEY AUTOINCREMENT";
        }

        StringBuilder column = new StringBuilder();
        column.append(fieldName).append(" ").append(typeName);
        if ("NO".equals(allowNull)) {
            column.append(" NOT NULL");
        }
        if (isSpecified(defaultValue)) {
            if (isCurrentKeyword(defaultValue)) {
                column.append(" DEFAULT ").append(defaultValue);
            } else {
                column.append(" DEFAULT '").append(defaultValue).append("'");
            }
        }
        return column.toString();
    }

    private boolean isSpecified(String value) {
        return value != null && !value.isEmpty() && !"null".equals(value);
    }

    private boolean isCurrentKeyword(String value) {
        String upper = value.toUpperCase();
        return "CURRENT_DATE".equals(upper) || "CURRENT_TIME".equals(upper) || "CURRENT_TIMESTAMP".equals(upper);
    }
}
