package com.freedom.remainz_v2.common.db;

import java.util.List;
import java.util.Map;

import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;
import com.freedom.remainz_v2.common.util.MsgUtil;

/**
 * テーブル定義から、SELECT文を生成するクラスです。
 *
 * <p>
 * 全カラムを定義順に列挙し、同じ並び順でORDER BYします（移植元のDBクエリの仕組みと同様、
 * 検索結果の順序を安定させるためです）。
 * </p>
 */
public class SelectSqlBuilder {

    private final MsgUtil msg;

    public SelectSqlBuilder() {
        this(new MsgUtil());
    }

    public SelectSqlBuilder(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * SELECT文を生成します。
     *
     * @param tableName  テーブル物理名
     * @param columnDefs テーブルのカラム定義
     * @return SELECT文
     */
    public String build(String tableName, List<Map<String, String>> columnDefs) {

        if (columnDefs == null || columnDefs.isEmpty()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.common.db.columnDefNotFound", tableName));
        }

        StringBuilder columnPart = new StringBuilder();
        for (Map<String, String> columnDef : columnDefs) {
            if (columnPart.length() > 0) {
                columnPart.append(", ");
            }
            columnPart.append(columnDef.get("FIELD_NAME"));
        }

        return "SELECT " + columnPart + " FROM " + tableName + " ORDER BY " + columnPart + ";";
    }
}
