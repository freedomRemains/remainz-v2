package com.freedom.remainz_v2.common.db;

/**
 * テーブル物理名から、DROP TABLE文を生成するクラスです。
 */
public class DropTableSqlBuilder {

    /**
     * DROP TABLE文を生成します。
     *
     * @param tableName テーブル物理名
     * @return DROP TABLE文
     */
    public String build(String tableName) {
        return "DROP TABLE IF EXISTS " + tableName + ";";
    }
}
