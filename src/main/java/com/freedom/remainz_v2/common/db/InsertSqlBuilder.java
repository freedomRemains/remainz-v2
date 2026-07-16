package com.freedom.remainz_v2.common.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;

/**
 * テーブル定義とテーブルデータから、INSERT文を生成するクラスです。
 *
 * <p>
 * カラムの型が「INT」の場合は数値としてそのまま出力し、それ以外の型はシングルクオートで囲みます。
 * データ上の値が「null」という文字列、または空文字列の場合は、数値型は「0」、それ以外は「NULL」として扱います。
 * </p>
 */
public class InsertSqlBuilder {

    /**
     * INSERT文を生成します。1レコード1文とし、テーブルのレコード数分のSQLをリストで返却します。
     *
     * @param tableName  テーブル物理名
     * @param columnDefs テーブルのカラム定義（型判定に使用）
     * @param dataRows   INSERT対象のデータ行
     * @return 生成したINSERT文のリスト
     */
    public List<String> build(String tableName, List<Map<String, String>> columnDefs,
            List<Map<String, String>> dataRows) {

        if (dataRows == null || dataRows.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> insertSqlList = new ArrayList<>();
        for (Map<String, String> dataRow : dataRows) {
            insertSqlList.add(buildInsertSql(tableName, columnDefs, dataRow));
        }
        return insertSqlList;
    }

    private String buildInsertSql(String tableName, List<Map<String, String>> columnDefs,
            Map<String, String> dataRow) {

        StringBuilder columnPart = new StringBuilder();
        StringBuilder valuePart = new StringBuilder();
        for (Map.Entry<String, String> entry : dataRow.entrySet()) {
            if (columnPart.length() > 0) {
                columnPart.append(", ");
                valuePart.append(", ");
            }
            columnPart.append(entry.getKey());
            valuePart.append(buildColumnValue(columnDefs, entry.getKey(), entry.getValue()));
        }

        return "INSERT INTO " + tableName + " (" + columnPart + ") VALUES (" + valuePart + ");";
    }

    private String buildColumnValue(List<Map<String, String>> columnDefs, String columnName, String columnValue) {

        boolean numeric = "INT".equalsIgnoreCase(findColumnType(columnDefs, columnName));

        if (numeric) {
            if (columnValue == null || columnValue.isEmpty() || "null".equals(columnValue)) {
                return "0";
            }
            return columnValue;
        }

        if (columnValue == null || "null".equals(columnValue)) {
            return "NULL";
        }
        return "'" + columnValue.replace("'", "''") + "'";
    }

    private String findColumnType(List<Map<String, String>> columnDefs, String columnName) {
        for (Map<String, String> columnDef : columnDefs) {
            if (columnName.equals(columnDef.get("FIELD_NAME"))) {
                return columnDef.get("TYPE_NAME");
            }
        }
        throw new BusinessRuleViolationException("カラム定義が見つかりません。columnName=" + columnName);
    }
}
