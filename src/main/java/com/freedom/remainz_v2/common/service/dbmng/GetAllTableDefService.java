package com.freedom.remainz_v2.common.service.dbmng;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.freedom.remainz_v2.common.config.DbMngProperties;
import com.freedom.remainz_v2.common.db.RecordQueryService;
import com.freedom.remainz_v2.common.db.TsvTableFileWriter;
import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.service.script.ScriptElementService;
import com.freedom.remainz_v2.common.util.MsgUtil;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * ライブDBの「TBL_DEF」テーブル全体を、作業ディレクトリ配下の定義ファイルへ書き出すサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetAllTableDefService}に相当します。「TBL_DEF」テーブル自身が
 * 全テーブル分のカラム定義をまとめて保持するため、テーブルごとに分割せず単一の
 * 「TBL_DEF.txt」として、{@code work-dir}配下の「10_dbdef」ディレクトリへ書き出します。
 * このファイルは、DB構成更新(リストア)時に{@code UpdateSqlByDefService}が読み込みます。
 * </p>
 */
@Service
public class GetAllTableDefService implements ScriptElementService {

    private static final String TBL_DEF_SQL = """
            SELECT *
            FROM TBL_DEF
            ORDER BY TBL_DEF_ID
            """;

    private static final String DBDEF_DIR_NAME = "10_dbdef";
    private static final String TBL_DEF_FILE_NAME = "TBL_DEF.txt";

    private final RecordQueryService recordQueryService;
    private final DbMngProperties dbMngProperties;
    private final TsvTableFileWriter tsvTableFileWriter;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    @Autowired
    public GetAllTableDefService(RecordQueryService recordQueryService, DbMngProperties dbMngProperties,
            ObjectMapper objectMapper, MsgUtil msg) {
        this(recordQueryService, dbMngProperties, new TsvTableFileWriter(msg), objectMapper, msg);
    }

    public GetAllTableDefService(RecordQueryService recordQueryService, DbMngProperties dbMngProperties,
            TsvTableFileWriter tsvTableFileWriter, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.dbMngProperties = dbMngProperties;
        this.tsvTableFileWriter = tsvTableFileWriter;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        // 入力コンテキストは使用しないが、他のスクリプト要素サービスとインターフェースを揃えるため受け取る
        DbMngJsonUtil.readAsObjectNode(objectMapper, msg, contextJson);

        // ライブDBのTBL_DEF全件を取得し、テーブル定義ファイルとして作業ディレクトリへ書き出す
        List<LinkedHashMap<String, String>> tblDefRows = recordQueryService.select(TBL_DEF_SQL);

        Path dbDefDir = Path.of(dbMngProperties.getWorkDir()).resolve(DBDEF_DIR_NAME);
        createDirectoryIfAbsent(dbDefDir);

        Path tblDefFilePath = dbDefDir.resolve(TBL_DEF_FILE_NAME);
        tsvTableFileWriter.write(tblDefFilePath, tblDefRows);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("dbDefFilePath", tblDefFilePath.toString());
        return DbMngJsonUtil.writeAsString(objectMapper, msg, output);
    }

    private void createDirectoryIfAbsent(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.directoryCreateFailed", dir), e);
        }
    }
}
