package com.freedom.remainz_v2.common.service.dbmng;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.remainz_v2.common.config.DbMngProperties;
import com.freedom.remainz_v2.common.db.RecordQueryService;
import com.freedom.remainz_v2.common.db.TsvTableFileWriter;
import com.freedom.remainz_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GetAllTableDefServiceTest {

    private static final String TBL_DEF_SQL = """
            SELECT *
            FROM TBL_DEF
            ORDER BY TBL_DEF_ID
            """;

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private DbMngProperties dbMngProperties;

    @Mock
    private TsvTableFileWriter tsvTableFileWriter;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private GetAllTableDefService getAllTableDefService;

    @BeforeEach
    void setUp() {
        getAllTableDefService = new GetAllTableDefService(recordQueryService, dbMngProperties, tsvTableFileWriter,
                objectMapper, new MsgUtil());
    }

    @Test
    void ライブDBのTBL_DEF全件を作業ディレクトリ配下のTBL_DEFファイルへ書き出せること(@TempDir Path tempDir)
            throws Exception {

        when(dbMngProperties.getWorkDir()).thenReturn(tempDir.toString());

        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("TBL_DEF_ID", "1000001");
        row.put("TABLE_NAME", "ACCNT");
        row.put("FIELD_NAME", "ACCNT_ID");
        ArrayList<LinkedHashMap<String, String>> rows = new ArrayList<>(List.of(row));
        when(recordQueryService.select(eq(TBL_DEF_SQL))).thenReturn(rows);

        String result = getAllTableDefService.execute("{}");

        Path expectedFilePath = tempDir.resolve("10_dbdef").resolve("TBL_DEF.txt");
        verify(tsvTableFileWriter).write(eq(expectedFilePath), eq(rows));
        assertThat(tempDir.resolve("10_dbdef")).exists();

        JsonNode resultNode = objectMapper.readTree(result);
        assertThat(resultNode.path("dbDefFilePath").asText()).isEqualTo(expectedFilePath.toString());
    }
}
