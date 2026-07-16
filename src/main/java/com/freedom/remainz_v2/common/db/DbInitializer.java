package com.freedom.remainz_v2.common.db;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時、「TBL_DEF」テーブルが存在しなければ、事前生成済みのSQL
 * （{@code classpath:db/sql/*.sql}）を使ってDROP／CREATE／INSERTを実行するクラスです。
 *
 * <p>
 * 生成済みのSQLファイルはクラスパス上のリソースとして読み込むため、開発時に限らず、
 * 起動可能jarにパッケージされた状態でも動作します。SQLファイル自体は
 * {@link DbSchemaSqlGenerator} により「db/data」配下の資材から生成し、
 * 「db/sql」配下に配置しておくものとします。
 * </p>
 */
@Component
public class DbInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DbInitializer.class);

    private static final String TBL_DEF_TABLE_NAME = "TBL_DEF";

    private final JdbcTemplate jdbcTemplate;
    private final DbInitializationService dbInitializationService;

    public DbInitializer(JdbcTemplate jdbcTemplate, DbInitializationService dbInitializationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbInitializationService = dbInitializationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (existsTblDefTable()) {
            logger.info("TBL_DEFテーブルが既に存在するため、初期化処理をスキップします。");
            return;
        }
        logger.info("TBL_DEFテーブルが存在しないため、初期テーブル／データの作成を行います。");
        dbInitializationService.initializeDatabase();
    }

    private boolean existsTblDefTable() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", String.class,
                TBL_DEF_TABLE_NAME);
        return !tableNames.isEmpty();
    }
}
