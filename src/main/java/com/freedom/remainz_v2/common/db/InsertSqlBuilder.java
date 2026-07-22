package com.freedom.remainz_v2.common.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;
import com.freedom.remainz_v2.common.util.MsgUtil;

/**
 * テーブル定義とテーブルデータから、INSERT文を生成するクラスです。
 *
 * <p>
 * カラムの型が「INT」の場合は数値としてそのまま出力し、それ以外の型はシングルクオートで囲みます。
 * データ上の値が「null」という文字列、または空文字列の場合は、数値型は「0」、それ以外は「NULL」として扱います。
 * </p>
 *
 * <p>
 * 移植元「remainz」の{@code GetTableInsertSqlService}と同様、既定では値中のシングルクオートの
 * エスケープ({@code '}→{@code ''})を行いません。手動で用意する
 * {@code src/main/resources/db/data/}配下の seed データファイルは、値中にシングルクオートを
 * 含める場合、あらかじめ{@code ''}のようにエスケープしておく運用としているためです
 * (例: {@code PARTS_ITEM.txt}の{@code ITEM_QUERY}列)。
 * </p>
 *
 * <p>
 * 一方、DBメンテナンス機能(バックアップ/リストア)ではライブDBの値をそのままTSVへ退避し、
 * 復元時にINSERT文へ変換するため、値中に(seedファイルのように事前エスケープされていない)
 * シングルクオートがそのまま含まれ得ます。この場合はSQL構文が壊れないよう
 * {@link #build(String, List, List, boolean)}に{@code escapeSingleQuotes=true}を指定して
 * 呼び出し、本クラス側で自動的にエスケープを行います。
 * </p>
 */
public class InsertSqlBuilder {

    private final MsgUtil msg;

    public InsertSqlBuilder() {
        this(new MsgUtil());
    }

    public InsertSqlBuilder(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * INSERT文を生成します。1レコード1文とし、テーブルのレコード数分のSQLをリストで返却します。
     *
     * <p>
     * 値中のシングルクオートはエスケープしません(データファイル側で事前エスケープ済みである前提)。
     * ライブDBの値をそのまま変換する場合は{@link #build(String, List, List, boolean)}を
     * 使用してください。
     * </p>
     *
     * @param tableName  テーブル物理名
     * @param columnDefs テーブルのカラム定義（型判定に使用）
     * @param dataRows   INSERT対象のデータ行
     * @return 生成したINSERT文のリスト
     */
    public List<String> build(String tableName, List<Map<String, String>> columnDefs,
            List<Map<String, String>> dataRows) {
        return build(tableName, columnDefs, dataRows, false);
    }

    /**
     * INSERT文を生成します。1レコード1文とし、テーブルのレコード数分のSQLをリストで返却します。
     *
     * @param tableName          テーブル物理名
     * @param columnDefs         テーブルのカラム定義（型判定に使用）
     * @param dataRows           INSERT対象のデータ行
     * @param escapeSingleQuotes {@code true}の場合、値中のシングルクオートを{@code ''}へ
     *                           自動エスケープする(ライブDBの値をそのまま変換する場合に使用)
     * @return 生成したINSERT文のリスト
     */
    public List<String> build(String tableName, List<Map<String, String>> columnDefs,
            List<Map<String, String>> dataRows, boolean escapeSingleQuotes) {

        // INSERT対象のデータ行がなければ、生成結果も空のまま返却する
        if (dataRows == null || dataRows.isEmpty()) {
            return new ArrayList<>();
        }

        // 各データ行を1件ずつINSERT文へ変換し、実行順を保った一覧にまとめる
        List<String> insertSqlList = new ArrayList<>();
        for (Map<String, String> dataRow : dataRows) {
            insertSqlList.add(buildInsertSql(tableName, columnDefs, dataRow, escapeSingleQuotes));
        }
        return insertSqlList;
    }

    private String buildInsertSql(String tableName, List<Map<String, String>> columnDefs,
            Map<String, String> dataRow, boolean escapeSingleQuotes) {

        // カラム一覧と値一覧を同じ順序で組み立て、1レコード分のINSERT文を生成する
        StringBuilder columnPart = new StringBuilder();
        StringBuilder valuePart = new StringBuilder();
        for (Map.Entry<String, String> entry : dataRow.entrySet()) {
            if (columnPart.length() > 0) {
                columnPart.append(", ");
                valuePart.append(", ");
            }
            columnPart.append(entry.getKey());
            valuePart.append(buildColumnValue(columnDefs, entry.getKey(), entry.getValue(), escapeSingleQuotes));
        }

        return "INSERT INTO " + tableName + " (" + columnPart + ") VALUES (" + valuePart + ");";
    }

    private String buildColumnValue(List<Map<String, String>> columnDefs, String columnName, String columnValue,
            boolean escapeSingleQuotes) {

        // 数値型はクオートせず、未設定値や"null"文字列は0で補完する
        boolean numeric = "INT".equalsIgnoreCase(findColumnType(columnDefs, columnName));

        if (numeric) {
            if (columnValue == null || columnValue.isEmpty() || "null".equals(columnValue)) {
                return "0";
            }
            return columnValue;
        }

        // 文字列系は"null"をNULLとして扱い、それ以外はシングルクオートで囲んで出力する
        if (columnValue == null || "null".equals(columnValue)) {
            return "NULL";
        }
        String value = escapeSingleQuotes ? columnValue.replace("'", "''") : columnValue;
        return "'" + value + "'";
    }

    private String findColumnType(List<Map<String, String>> columnDefs, String columnName) {
        for (Map<String, String> columnDef : columnDefs) {
            if (columnName.equals(columnDef.get("FIELD_NAME"))) {
                return columnDef.get("TYPE_NAME");
            }
        }
        throw new BusinessRuleViolationException(msg.get("msg.err.common.db.columnDefForColumnNotFound", columnName));
    }
}
