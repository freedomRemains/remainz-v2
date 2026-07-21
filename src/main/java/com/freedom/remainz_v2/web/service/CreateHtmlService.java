package com.freedom.remainz_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.freedom.remainz_v2.common.db.RecordQueryService;
import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.service.script.ScriptElementService;
import com.freedom.remainz_v2.common.util.MsgUtil;
import com.freedom.remainz_v2.common.util.VariablePlaceholderResolver;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * リクエストURIに対応する画面パーツ・画面表示項目を取得し、応答種別・遷移先を決定するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code CreateHtmlService}に相当します。移植元は{@code ITEM_KEY}を
 * トップレベルのキーとして展開するため同一画面に同じパーツが複数あると衝突しますが、本移植では
 * {@code htmlPage}配列の各要素({@code PARTS_IN_PAGE_ID}単位)に画面表示項目を{@code items}配列
 * としてネストする構造に変更しています。
 * </p>
 */
@Service
public class CreateHtmlService implements ScriptElementService {

    private static final String PAGE_SQL = """
            SELECT
                A.PARTS_IN_PAGE_ID, B.HTML_PAGE_ID, B.PAGE_NAME, B.RESP_KIND_GET,
                B.RESP_KIND_POST, B.RESP_KIND_PUT, B.RESP_KIND_DELETE,
                B.DESTINATION_GET, B.DESTINATION_POST, B.DESTINATION_PUT,
                B.DESTINATION_DELETE, C.HTML_PARTS_ID, C.PARTS_NAME,
                D.PARTS_ITEM_ID, D.ITEM_KEY, D.ITEM_QUERY
            FROM PARTS_IN_PAGE A
            LEFT JOIN HTML_PAGE B ON A.HTML_PAGE_ID = B.HTML_PAGE_ID
            LEFT JOIN HTML_PARTS C ON A.HTML_PARTS_ID = C.HTML_PARTS_ID
            LEFT JOIN PARTS_ITEM D ON A.PARTS_IN_PAGE_ID = D.PARTS_IN_PAGE_ID
            LEFT JOIN URI_PATTERN E ON B.URI_PATTERN_ID = E.URI_PATTERN_ID
            WHERE E.URI_PATTERN = ?
            ORDER BY A.ORD_IN_GRP, D.ORD_IN_GRP
            """;

    private final RecordQueryService recordQueryService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public CreateHtmlService(RecordQueryService recordQueryService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);

        String requestUri = context.path("requestUri").asString("");
        String requestKind = context.path("requestKind").asString("");

        if (context.path("errMsgKey").asString("").isBlank()) {
            context.put("errMsgKey", "0");
        }

        List<LinkedHashMap<String, String>> pageRows = recordQueryService.select(PAGE_SQL, List.of(requestUri));
        if (pageRows.isEmpty()) {
            throw new ApplicationInternalException(msg.get("msg.err.web.pageNotFound", requestUri));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.set("htmlPage", buildHtmlPage(pageRows, context));

        String existingRespKind = context.path("respKind").asString("");
        output.put("respKind", existingRespKind.isBlank()
                ? pageRows.get(0).get("RESP_KIND_" + requestKind)
                : existingRespKind);

        String existingDestination = context.path("destination").asString("");
        output.put("destination", existingDestination.isBlank()
                ? VariablePlaceholderResolver.resolve(pageRows.get(0).get("DESTINATION_" + requestKind), context, msg)
                : existingDestination);

        return writeAsString(output);
    }

    private ArrayNode buildHtmlPage(List<LinkedHashMap<String, String>> pageRows, ObjectNode context) {

        ArrayNode htmlPage = objectMapper.createArrayNode();
        Map<String, ObjectNode> partsInPageById = new LinkedHashMap<>();

        for (LinkedHashMap<String, String> row : pageRows) {

            String partsInPageId = row.get("PARTS_IN_PAGE_ID");
            ObjectNode partsInPage = partsInPageById.get(partsInPageId);
            if (partsInPage == null) {
                partsInPage = objectMapper.createObjectNode();
                partsInPage.put("partsInPageId", partsInPageId);
                partsInPage.put("htmlPartsId", row.get("HTML_PARTS_ID"));
                partsInPage.put("partsName", row.get("PARTS_NAME"));
                partsInPage.set("items", objectMapper.createArrayNode());
                partsInPageById.put(partsInPageId, partsInPage);
                htmlPage.add(partsInPage);
            }

            String itemQuery = row.get("ITEM_QUERY");
            if (itemQuery != null && !itemQuery.isBlank()) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("itemKey", row.get("ITEM_KEY"));
                item.set("records", selectItem(itemQuery, context));
                ((ArrayNode) partsInPage.get("items")).add(item);
            }
        }

        return htmlPage;
    }

    private ArrayNode selectItem(String itemQuery, ObjectNode context) {

        String sql = VariablePlaceholderResolver.resolve(itemQuery, context, msg);
        List<LinkedHashMap<String, String>> recordList = recordQueryService.select(sql);

        ArrayNode records = objectMapper.createArrayNode();
        for (LinkedHashMap<String, String> record : recordList) {
            ObjectNode recordNode = objectMapper.createObjectNode();
            for (Map.Entry<String, String> entry : record.entrySet()) {
                recordNode.put(entry.getKey(),
                        VariablePlaceholderResolver.resolve(entry.getValue(), context, msg));
            }
            records.add(recordNode);
        }
        return records;
    }

    private ObjectNode readAsObjectNode(String json) {
        try {
            return (ObjectNode) objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", json), e);
        }
    }

    private String writeAsString(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new ApplicationInternalException(msg.get("msg.err.web.jsonProcessingFailed", node), e);
        }
    }
}
