package com.freedom.remainz_v2.web.service;

import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.freedom.remainz_v2.common.db.RecordQueryService;
import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;
import com.freedom.remainz_v2.common.service.script.ScriptElementService;
import com.freedom.remainz_v2.common.util.MsgUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * アカウント情報と権限一覧を取得し、ロール制約を確認するサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code GetAccountService}に相当します。
 * </p>
 */
@Service
public class GetAccountService implements ScriptElementService {

    /** デフォルトアカウントID(ゲストアカウント) */
    private static final String DEFAULT_ACCNT_ID = "1000001";

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.ACCNT_ID = ?
            """;

    private static final String AUTH_SQL = """
            SELECT
                B.HTML_PARTS_ID, B.AUTH_KIND
            FROM APROLE_IN_ACCNT A
            LEFT JOIN HTML_PARTS_IN_APROLE B ON A.APROLE_ID = B.APROLE_ID
            WHERE A.ACCNT_ID = ?
            ORDER BY B.HTML_PARTS_ID
            """;

    private static final String ROLE_RESTRICTION_SQL = """
            SELECT
                B.APROLE_ID
            FROM HTML_PAGE A
            LEFT JOIN REQUIRE_APROLE B ON A.HTML_PAGE_ID = B.HTML_PAGE_ID
            LEFT JOIN URI_PATTERN C ON A.URI_PATTERN_ID = C.URI_PATTERN_ID
            WHERE C.URI_PATTERN = ?
            GROUP BY B.APROLE_ID
            ORDER BY B.APROLE_ID
            """;

    private static final String ROLE_SQL = """
            SELECT
                A.APROLE_ID, B.ROLE_NAME
            FROM APROLE_IN_ACCNT A
            LEFT JOIN APROLE B ON A.APROLE_ID = B.APROLE_ID
            WHERE A.ACCNT_ID = ?
            ORDER BY A.APROLE_ID
            """;

    private final RecordQueryService recordQueryService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public GetAccountService(RecordQueryService recordQueryService, ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);

        String accountId = context.path("accountId").asString("");
        if (accountId.isBlank()) {
            accountId = DEFAULT_ACCNT_ID;
        }

        List<LinkedHashMap<String, String>> account = recordQueryService.select(ACCOUNT_SQL, List.of(accountId));
        List<LinkedHashMap<String, String>> authList = recordQueryService.select(AUTH_SQL, List.of(accountId));

        checkRequireRole(context.path("requestUri").asString(""), accountId);

        ObjectNode output = objectMapper.createObjectNode();
        output.put("accountId", accountId);
        output.putPOJO("account", account);
        output.putPOJO("authList", authList);

        return writeAsString(output);
    }

    private void checkRequireRole(String requestUri, String accountId) {

        List<LinkedHashMap<String, String>> restrictionRows =
                recordQueryService.select(ROLE_RESTRICTION_SQL, List.of(requestUri));
        List<LinkedHashMap<String, String>> roleRows = recordQueryService.select(ROLE_SQL, List.of(accountId));

        for (LinkedHashMap<String, String> restrictionRow : restrictionRows) {

            String requiredApRoleId = restrictionRow.get("APROLE_ID");
            if (requiredApRoleId == null || requiredApRoleId.isBlank()) {
                // そもそもロール制約がない場合は制約違反なしと判断する
                return;
            }

            for (LinkedHashMap<String, String> roleRow : roleRows) {
                if (requiredApRoleId.equals(roleRow.get("APROLE_ID"))) {
                    // アカウントがロールを持っていれば制約違反なしと判断する
                    return;
                }
            }
        }

        throw new BusinessRuleViolationException(msg.get("msg.err.web.roleRestriction", requestUri));
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
