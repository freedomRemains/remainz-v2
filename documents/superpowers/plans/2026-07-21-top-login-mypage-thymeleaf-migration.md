# TOP/ログイン/マイページ Thymeleaf移植 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** issue #11に基づき、移植元「remainz」のマイページ(ログイン処理含む)をSpring Boot + Thymeleafへ移植し、`GET/POST /remainz-v2/service/myPage.html` が動作するようにする。

**Architecture:** 既存のDBレコード駆動アーキテクチャ(`URI_PATTERN`→`HTML_PAGE`→`SCR`/`SCR_ELM`→`ScriptElementService`実装群→`PARTS_IN_PAGE`/`HTML_PARTS`/`PARTS_ITEM`)をそのまま利用する。新規に`LoginService`(認証、失敗時はPRGパターンでリダイレクト)、`ErrMsgService`(エラーメッセージ発行、DB書込)、`AuthUtil`(権限判定、静的)、`HtmlPageItemUtil`(画面表示項目のパート横断検索、静的)を追加し、`CreateHtmlService`に2つの修正(`errMsgKey`デフォルト化、`respKind`/`destination`上書き防止ガード)を加える。フロントエンドはThymeleafフラグメントを`10xxx`(権限確認ラッパー)/`common/20xxx`(本体)の2ファイル構成で追加する。

**Tech Stack:** Java 21 / Spring Boot 4.1.0 (Web MVC, JDBC, Thymeleaf) / Jackson 3.x(`tools.jackson.*`) / JUnit 5 + Mockito + AssertJ / SQLite(`org.xerial:sqlite-jdbc`)。

## Global Constraints

- 設計ドキュメント: `documents/design/2000005_top_login_mypage_thymeleaf_migration.md`(本プランの一次資料)。
- issue: [#11](https://github.com/freedomRemains/remainz-v2/issues/11)。ブランチは`feature/11`(既にチェックアウト済み)。
- ビルド/テストコマンドは`./gradlew`を使用する。ローカル実行時、JDK 21が必要(`JAVA_HOME`が21を指していない場合は`./gradlew`実行前に確認すること)。
- 例外は`BusinessRuleViolationException`(業務ルール違反)/`ApplicationInternalException`(技術的失敗)のいずれも`RuntimeException`派生で`throws`不要。
- ログの`ERROR`/`WARN`メッセージは必ず`MsgUtil`(`msg.get(key, args...)`、`src/main/resources/msg/messages.properties`)経由とする。ハードコードした文字列を使わない。
- JSON入出力は`tools.jackson.databind.ObjectMapper`/`ObjectNode`/`JsonNode`を使用する(Jackson 3.x、`com.fasterxml.jackson.*`ではない)。`JsonNode.asString()`を使う(非推奨の`asText()`は使わない)。
- DB書き込みが必要な場合を除き、DBアクセスは`RecordQueryService.select(sql, params)`(SELECT専用)を利用する。
- 新規実装クラスには必ず対応するテストクラスを用意する(Mockito、`@ExtendWith(MockitoExtension.class)`、テストメソッド名は日本語)。
- DBデータ(`src/main/resources/db/data/*.txt`)を変更した場合は、必ず`DbSchemaSqlGeneratorRealDataTest`を実行して`src/main/resources/db/sql/*.sql`を再生成し、生成物もコミットする。
- 各タスックの完了時に必ずコミットする。コミットメッセージ末尾に以下のトレーラーを付与する:
  ```
  Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
  ```

---

### Task 1: AuthUtilの新設

**Files:**
- Create: `src/main/java/com/freedom/remainz_v2/web/util/AuthUtil.java`
- Test: `src/test/java/com/freedom/remainz_v2/web/util/AuthUtilTest.java`

**Interfaces:**
- Consumes: なし(DBアクセスを伴わない静的ロジックのみ)。
- Produces:
  - `AuthUtil.hasAuth(String htmlPartsId, List<Map<String, Object>> authList): boolean`
  - `AuthUtil.hasReadAuth(String htmlPartsId, List<Map<String, Object>> authList): boolean`
  - `AuthUtil.hasEditAuth(String htmlPartsId, List<Map<String, Object>> authList): boolean`
  - これらはThymeleafテンプレートから`T(com.freedom.remainz_v2.web.util.AuthUtil).hasReadAuth('1000001', authList)`のようにSpringELの静的メソッド構文で呼び出される(Task 9で使用)。`authList`はモデル属性(`GetAccountService`が出力するJSONの`authList`配列を`Map<String,Object>`のリストへ変換したもの)。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/remainz_v2/web/util/AuthUtilTest.java`:
```java
package com.freedom.remainz_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link AuthUtil}のテストです。
 */
class AuthUtilTest {

    private Map<String, Object> authRow(String htmlPartsId, String authKind) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("HTML_PARTS_ID", htmlPartsId);
        row.put("AUTH_KIND", authKind);
        return row;
    }

    @Test
    void hasAuthは対象のHTML_PARTS_IDが権限一覧に存在すればtrueを返すこと() {

        List<Map<String, Object>> authList = List.of(authRow("1000001", "read"));

        assertThat(AuthUtil.hasAuth("1000001", authList)).isTrue();
        assertThat(AuthUtil.hasAuth("9999999", authList)).isFalse();
    }

    @Test
    void hasReadAuthはAUTH_KINDがreadの場合のみtrueを返すこと() {

        List<Map<String, Object>> authList = List.of(authRow("1000201", "edit"));

        assertThat(AuthUtil.hasReadAuth("1000201", authList)).isFalse();
        assertThat(AuthUtil.hasEditAuth("1000201", authList)).isTrue();
    }

    @Test
    void 権限一覧が空の場合はいずれもfalseを返すこと() {

        List<Map<String, Object>> authList = List.of();

        assertThat(AuthUtil.hasAuth("1000001", authList)).isFalse();
        assertThat(AuthUtil.hasReadAuth("1000001", authList)).isFalse();
        assertThat(AuthUtil.hasEditAuth("1000001", authList)).isFalse();
    }
}
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.util.AuthUtilTest"`
Expected: FAIL (コンパイルエラー、`AuthUtil`クラスが存在しない)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/util/AuthUtil.java`:
```java
package com.freedom.remainz_v2.web.util;

import java.util.List;
import java.util.Map;

/**
 * 権限判定ユーティリティです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.util.AuthUtil}のうち、DBアクセスを伴わない
 * {@code hasAuth}/{@code hasReadAuth}/{@code hasEditAuth}のみを移植しています。DB取得系の
 * メソッドは{@code GetAccountService}が既にカバーしているため対象外です。Thymeleafテンプレート
 * からはSpringELの静的メソッド構文({@code T(...).hasReadAuth(...)})で呼び出されます。
 * </p>
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    /**
     * アカウントが指定した画面パーツに対する権限(read/editいずれか)を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト({@code HTML_PARTS_ID}/{@code AUTH_KIND}を含む)
     * @return 権限を持っている場合は{@code true}
     */
    public static boolean hasAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        for (Map<String, Object> row : authList) {
            if (htmlPartsId.equals(row.get("HTML_PARTS_ID"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * アカウントが指定した画面パーツに対するread権限を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト
     * @return read権限を持っている場合は{@code true}
     */
    public static boolean hasReadAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        return hasAuthKind(htmlPartsId, "read", authList);
    }

    /**
     * アカウントが指定した画面パーツに対するedit権限を持っているか判定します。
     *
     * @param htmlPartsId 画面パーツマスタID
     * @param authList    アカウントに紐づく権限のリスト
     * @return edit権限を持っている場合は{@code true}
     */
    public static boolean hasEditAuth(String htmlPartsId, List<Map<String, Object>> authList) {
        return hasAuthKind(htmlPartsId, "edit", authList);
    }

    private static boolean hasAuthKind(String htmlPartsId, String authKind, List<Map<String, Object>> authList) {
        for (Map<String, Object> row : authList) {
            if (htmlPartsId.equals(row.get("HTML_PARTS_ID")) && authKind.equals(row.get("AUTH_KIND"))) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.util.AuthUtilTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/util/AuthUtil.java \
        src/test/java/com/freedom/remainz_v2/web/util/AuthUtilTest.java
git commit -m "issue #11: AuthUtilを新設

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 2: HtmlPageItemUtilの新設

**Files:**
- Create: `src/main/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtil.java`
- Test: `src/test/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtilTest.java`

**Interfaces:**
- Consumes: なし。
- Produces: `HtmlPageItemUtil.findRecords(List<Map<String, Object>> htmlPage, String itemKey): List<Map<String, Object>>`
  - `htmlPage`はモデル属性(`CreateHtmlService`が出力するJSONの`htmlPage`配列を`Map<String,Object>`のリストへ変換したもの。各要素は`items`キーに`{itemKey, records}`のリストを持つ)。
  - 該当する`itemKey`が見つからない場合は空リスト(`List.of()`)を返す。
  - Task 9のThymeleafフラグメントから`T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'urlLink')`のように呼び出される。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtilTest.java`:
```java
package com.freedom.remainz_v2.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * {@link HtmlPageItemUtil}のテストです。
 */
class HtmlPageItemUtilTest {

    private Map<String, Object> part(String partsInPageId, String htmlPartsId, List<Map<String, Object>> items) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("partsInPageId", partsInPageId);
        part.put("htmlPartsId", htmlPartsId);
        part.put("items", items);
        return part;
    }

    private Map<String, Object> item(String itemKey, List<Map<String, Object>> records) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("itemKey", itemKey);
        item.put("records", records);
        return item;
    }

    private Map<String, Object> record(String key, String value) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(key, value);
        return record;
    }

    @Test
    void 自分自身と異なるpartにネストされたitemKeyのレコードも取得できること() {

        List<Map<String, Object>> urlLinkRecords = List.of(record("URI_PATTERN", "/remainz-v2/service/top.html"));
        Map<String, Object> systemNamePart = part("1000201", "1000001",
                List.of(item("systemName", List.of(record("GNR_VAL", "Remainz")))));
        Map<String, Object> headerPart = part("1000202", "1000002", List.of(item("urlLink", urlLinkRecords)));

        List<Map<String, Object>> htmlPage = List.of(systemNamePart, headerPart);

        List<Map<String, Object>> result = HtmlPageItemUtil.findRecords(htmlPage, "urlLink");

        assertThat(result).isEqualTo(urlLinkRecords);
    }

    @Test
    void 該当するitemKeyが存在しない場合は空リストを返すこと() {

        Map<String, Object> part = part("1000201", "1000001",
                List.of(item("systemName", List.of(record("GNR_VAL", "Remainz")))));

        List<Map<String, Object>> result = HtmlPageItemUtil.findRecords(List.of(part), "linkList");

        assertThat(result).isEmpty();
    }

    @Test
    void itemsが存在しないpartがあっても例外にならず処理を継続できること() {

        Map<String, Object> partWithoutItems = new LinkedHashMap<>();
        partWithoutItems.put("partsInPageId", "1000201");
        partWithoutItems.put("htmlPartsId", "1000001");

        Map<String, Object> partWithItem = part("1000202", "1000002",
                List.of(item("urlLink", List.of(record("URI_PATTERN", "/remainz-v2/service/top.html")))));

        List<Map<String, Object>> result =
                HtmlPageItemUtil.findRecords(List.of(partWithoutItems, partWithItem), "urlLink");

        assertThat(result).hasSize(1);
    }
}
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.util.HtmlPageItemUtilTest"`
Expected: FAIL (コンパイルエラー、`HtmlPageItemUtil`クラスが存在しない)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtil.java`:
```java
package com.freedom.remainz_v2.web.util;

import java.util.List;
import java.util.Map;

/**
 * {@code htmlPage}配列内の画面表示項目({@code items})を、{@code itemKey}を指定して
 * パートを横断して検索するユーティリティです。
 *
 * <p>
 * {@code CreateHtmlService}が出力する{@code htmlPage}構造は、画面表示項目を
 * {@code PARTS_IN_PAGE_ID}(パート)単位でネストします。しかし、あるパート(例:
 * ヘッダー、{@code HTML_PARTS_ID=1000001})の描画に必要な項目(例: {@code urlLink})が、
 * DB上は別のパート({@code HTML_PARTS_ID=1000002})にネストされている場合があるため、
 * Thymeleafテンプレート側で{@code itemKey}を指定してパートを横断的に検索できるようにします。
 * </p>
 */
public final class HtmlPageItemUtil {

    private HtmlPageItemUtil() {
    }

    /**
     * {@code htmlPage}配列全体から、指定した{@code itemKey}を持つ画面表示項目のレコードを検索します。
     *
     * @param htmlPage {@code CreateHtmlService}が出力した{@code htmlPage}配列(Map化済み)
     * @param itemKey  検索対象の項目キー({@code PARTS_ITEM.ITEM_KEY})
     * @return 該当する項目のレコード一覧。見つからない場合は空リスト
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> findRecords(List<Map<String, Object>> htmlPage, String itemKey) {

        for (Map<String, Object> part : htmlPage) {

            Object itemsObj = part.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                continue;
            }

            for (Object itemObj : items) {
                Map<String, Object> item = (Map<String, Object>) itemObj;
                if (itemKey.equals(item.get("itemKey"))) {
                    return (List<Map<String, Object>>) item.get("records");
                }
            }
        }

        return List.of();
    }
}
```

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.util.HtmlPageItemUtilTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtil.java \
        src/test/java/com/freedom/remainz_v2/web/util/HtmlPageItemUtilTest.java
git commit -m "issue #11: HtmlPageItemUtilを新設

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 3: メッセージキーの追加(msg.err.web.requiredParamMissing)

**Files:**
- Modify: `src/main/resources/msg/messages.properties`

**Interfaces:**
- Consumes: なし。
- Produces: `msg.get("msg.err.web.requiredParamMissing", paramName)` がTask 4の`LoginService`で使用される。

- [ ] **Step 1: メッセージキーを追加する**

`src/main/resources/msg/messages.properties`の`# message (web)`セクション末尾に追記する:
```properties
msg.err.web.requiredParamMissing=必須パラメータが指定されていません。paramName={0}
```

- [ ] **Step 2: 追加したキーが読み込めることを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.common.util.MsgUtilTest"`
Expected: PASS (既存テストが引き続き通ることを確認。既存テストに変更は無い)

- [ ] **Step 3: コミットする**

```bash
git add src/main/resources/msg/messages.properties
git commit -m "issue #11: 必須パラメータ欠如用のメッセージキーを追加

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 4: ErrMsgServiceの新設

**Files:**
- Create: `src/main/java/com/freedom/remainz_v2/web/service/ErrMsgService.java`
- Test: `src/test/java/com/freedom/remainz_v2/web/service/ErrMsgServiceTest.java`

**Interfaces:**
- Consumes: `RecordQueryService.select(String sql, List<String> params): ArrayList<LinkedHashMap<String, String>>`(既存)。`org.springframework.jdbc.core.JdbcTemplate`(Springが提供するBean)。
- Produces: `ErrMsgService.getErrMsgKey(String sessionId, String accountId, String gnrKeyValId): String`
  - `GNR_KEY_VAL`から`gnrKeyValId`に対応するメッセージを取得し、`ERR_MSG`テーブルへ1レコード追加した上で、そのレコードの`ERR_MSG_ID`(文字列)を返す。
  - `GNR_KEY_VAL`にレコードが存在しない場合は`"0"`を返す(移植元`ErrMsgUtil.getErrMsgKey`の挙動を踏襲)。
  - Task 5の`LoginService`から呼び出される。

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/remainz_v2/web/service/ErrMsgServiceTest.java`:
```java
package com.freedom.remainz_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import com.freedom.remainz_v2.common.db.RecordQueryService;

/**
 * {@link ErrMsgService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class ErrMsgServiceTest {

    private static final String GNR_VAL_SQL = "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY_VAL_ID = ?";

    @Mock
    private RecordQueryService recordQueryService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ErrMsgService errMsgService;

    @BeforeEach
    void setUp() {
        errMsgService = new ErrMsgService(recordQueryService, jdbcTemplate);
    }

    @Test
    void メッセージが存在する場合はERR_MSGへ登録され採番されたIDが返却されること() {

        LinkedHashMap<String, String> gnrValRow = new LinkedHashMap<>();
        gnrValRow.put("GNR_VAL", "メールアドレスもしくはパスワードが間違っています。");
        when(recordQueryService.select(eq(GNR_VAL_SQL), eq(List.of("1000401"))))
                .thenReturn(new ArrayList<>(List.of(gnrValRow)));

        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            Map<String, Object> generatedKey = new LinkedHashMap<>();
            generatedKey.put("ERR_MSG_ID", 123);
            keyHolder.getKeyList().add(generatedKey);
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        String errMsgKey = errMsgService.getErrMsgKey("session-1", "1000001", "1000401");

        assertThat(errMsgKey).isEqualTo("123");
    }

    @Test
    void 汎用キー値マスタにメッセージが存在しない場合は固定値0が返却されること() {

        when(recordQueryService.select(eq(GNR_VAL_SQL), eq(List.of("9999999"))))
                .thenReturn(new ArrayList<>());

        String errMsgKey = errMsgService.getErrMsgKey("session-1", "1000001", "9999999");

        assertThat(errMsgKey).isEqualTo("0");
    }
}
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.ErrMsgServiceTest"`
Expected: FAIL (コンパイルエラー、`ErrMsgService`クラスが存在しない)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/service/ErrMsgService.java`:
```java
package com.freedom.remainz_v2.web.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.freedom.remainz_v2.common.db.RecordQueryService;

/**
 * エラーメッセージの発行・登録を行うサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.util.ErrMsgUtil}に相当します。{@code GNR_KEY_VAL}の
 * 参照と{@code ERR_MSG}への書込みの両方を行うため、静的utilではなく{@code RecordQueryService}
 * (SELECT専用)と{@link JdbcTemplate}(INSERT用)を注入した{@code @Service}として実装します。
 * </p>
 */
@Service
public class ErrMsgService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String GNR_VAL_SQL = "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY_VAL_ID = ?";

    private static final String INSERT_ERR_MSG_SQL = """
            INSERT INTO ERR_MSG
                (SESSION_ID, ACCNT_ID, ERR_MSG, VERSION, IS_DELETED, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT)
            VALUES (?, ?, ?, 1, 0, ?, ?, ?, ?)
            """;

    private final RecordQueryService recordQueryService;
    private final JdbcTemplate jdbcTemplate;

    public ErrMsgService(RecordQueryService recordQueryService, JdbcTemplate jdbcTemplate) {
        this.recordQueryService = recordQueryService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 汎用キー値マスタからエラーメッセージを取得し、{@code ERR_MSG}へ登録した上で
     * 登録したレコードのIDを返却します。
     *
     * @param sessionId   セッションID
     * @param accountId   アカウントID
     * @param gnrKeyValId 汎用キー値マスタID({@code GNR_KEY_VAL_ID})
     * @return 登録したエラーメッセージのID(文字列)。汎用キー値マスタにメッセージが存在しない場合は{@code "0"}
     */
    public String getErrMsgKey(String sessionId, String accountId, String gnrKeyValId) {

        List<LinkedHashMap<String, String>> gnrValRows = recordQueryService.select(GNR_VAL_SQL, List.of(gnrKeyValId));
        if (gnrValRows.isEmpty()) {
            return "0";
        }

        String errMsg = gnrValRows.get(0).get("GNR_VAL");
        String currentDate = LocalDateTime.now().format(DATE_FORMAT);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_ERR_MSG_SQL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionId);
            ps.setString(2, accountId);
            ps.setString(3, errMsg);
            ps.setString(4, accountId);
            ps.setString(5, currentDate);
            ps.setString(6, accountId);
            ps.setString(7, currentDate);
            return ps;
        }, keyHolder);

        return String.valueOf(keyHolder.getKey().longValue());
    }
}
```

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.ErrMsgServiceTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/service/ErrMsgService.java \
        src/test/java/com/freedom/remainz_v2/web/service/ErrMsgServiceTest.java
git commit -m "issue #11: ErrMsgServiceを新設

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 5: LoginServiceの新設

**Files:**
- Create: `src/main/java/com/freedom/remainz_v2/web/service/LoginService.java`
- Test: `src/test/java/com/freedom/remainz_v2/web/service/LoginServiceTest.java`

**Interfaces:**
- Consumes:
  - `RecordQueryService.select(String sql, List<String> params)`(既存)
  - `ErrMsgService.getErrMsgKey(String sessionId, String accountId, String gnrKeyValId): String`(Task 4)
  - `ScriptElementService`インターフェース(既存、`String execute(String contextJson)`)
- Produces: `LoginService`は`ScriptElementService`実装として`SCR_ELM`(Task 7で設定)から呼び出される。
  - 認証成功時の出力JSON: `{"accountId": "<認証済みアカウントID>"}`
  - 認証失敗時の出力JSON: `{"respKind": "redirect", "destination": "myPage.html?errMsgKey=<キー>"}`

- [ ] **Step 1: 失敗するテストを書く**

`src/test/java/com/freedom/remainz_v2/web/service/LoginServiceTest.java`:
```java
package com.freedom.remainz_v2.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;
import com.freedom.remainz_v2.common.util.MsgUtil;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link LoginService}のテストです。
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS, A.PASSWORD,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.MAIL_ADDRESS = ?
            """;

    @Mock
    private com.freedom.remainz_v2.common.db.RecordQueryService recordQueryService;

    @Mock
    private ErrMsgService errMsgService;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(recordQueryService, errMsgService, JsonMapper.builder().build(),
                new MsgUtil());
    }

    private LinkedHashMap<String, String> accountRow(String accntId, String password) {
        LinkedHashMap<String, String> row = new LinkedHashMap<>();
        row.put("ACCNT_ID", accntId);
        row.put("PASSWORD", password);
        return row;
    }

    @Test
    void 認証成功時はaccountIdのみを出力しrespKindを設定しないこと() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("grandmaster@account.com"))))
                .thenReturn(new ArrayList<>(List.of(accountRow("1000401", "password"))));

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"grandmaster@account.com\",\"PASSWORD\":\"password\","
                        + "\"sessionId\":\"session-1\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("accountId").asString()).isEqualTo("1000401");
        assertThat(node.has("respKind")).isFalse();
    }

    @Test
    void パスワード不一致の場合はrespKindにredirectとerrMsgKey付きdestinationが設定されること() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("grandmaster@account.com"))))
                .thenReturn(new ArrayList<>(List.of(accountRow("1000401", "password"))));
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000401")).thenReturn("5");

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"grandmaster@account.com\",\"PASSWORD\":\"wrong\","
                        + "\"sessionId\":\"session-1\",\"accountId\":\"1000001\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
        assertThat(node.path("destination").asString()).isEqualTo("myPage.html?errMsgKey=5");
    }

    @Test
    void 該当メールアドレスが存在しない場合もログイン失敗として扱われること() {

        when(recordQueryService.select(eq(ACCOUNT_SQL), eq(List.of("unknown@account.com"))))
                .thenReturn(new ArrayList<>());
        when(errMsgService.getErrMsgKey("session-1", "1000001", "1000401")).thenReturn("0");

        String result = loginService.execute(
                "{\"MAIL_ADDRESS\":\"unknown@account.com\",\"PASSWORD\":\"password\","
                        + "\"sessionId\":\"session-1\",\"accountId\":\"1000001\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
    }

    @Test
    void MAIL_ADDRESSが未指定の場合は業務例外がスローされること() {

        assertThatThrownBy(() -> loginService.execute("{\"PASSWORD\":\"password\"}"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.LoginServiceTest"`
Expected: FAIL (コンパイルエラー、`LoginService`クラスが存在しない)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/service/LoginService.java`:
```java
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
 * ログイン認証を行うサービスです。
 *
 * <p>
 * 移植元「remainz」の{@code com.remainz.web.service.web.LoginService}に相当します。認証失敗時は
 * 例外をスローせず、PRG(Post/Redirect/Get)パターンにより、{@code respKind=redirect}、
 * {@code destination=myPage.html?errMsgKey=<キー>}を出力JSONに設定して正常終了します。これは
 * UI遷移制御のための意図的な設計であり、通常の例外方針(業務/システム例外)の対象外とします。
 * </p>
 */
@Service
public class LoginService implements ScriptElementService {

    /** ログイン失敗時のエラーメッセージに対応する汎用キー値マスタID */
    private static final String LOGIN_ERROR_GNR_KEY_VAL_ID = "1000401";

    private static final String ACCOUNT_SQL = """
            SELECT
                A.ACCNT_ID, A.ACCOUNT_NAME, A.MAIL_ADDRESS, A.PASSWORD,
                A.VERSION, A.IS_DELETED, A.CREATED_BY, A.CREATED_AT,
                A.UPDATED_BY, A.UPDATED_AT
            FROM ACCNT A
            WHERE A.MAIL_ADDRESS = ?
            """;

    private final RecordQueryService recordQueryService;
    private final ErrMsgService errMsgService;
    private final ObjectMapper objectMapper;
    private final MsgUtil msg;

    public LoginService(RecordQueryService recordQueryService, ErrMsgService errMsgService,
            ObjectMapper objectMapper, MsgUtil msg) {
        this.recordQueryService = recordQueryService;
        this.errMsgService = errMsgService;
        this.objectMapper = objectMapper;
        this.msg = msg;
    }

    @Override
    public String execute(String contextJson) {

        ObjectNode context = readAsObjectNode(contextJson);

        String mailAddress = context.path("MAIL_ADDRESS").asString("");
        if (mailAddress.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "MAIL_ADDRESS"));
        }
        String password = context.path("PASSWORD").asString("");
        if (password.isBlank()) {
            throw new BusinessRuleViolationException(msg.get("msg.err.web.requiredParamMissing", "PASSWORD"));
        }

        ObjectNode output = objectMapper.createObjectNode();

        String authenticatedAccountId = authenticate(mailAddress, password);
        if (authenticatedAccountId != null) {
            output.put("accountId", authenticatedAccountId);
            return writeAsString(output);
        }

        String sessionId = context.path("sessionId").asString("");
        String accountId = context.path("accountId").asString("");
        String errMsgKey = errMsgService.getErrMsgKey(sessionId, accountId, LOGIN_ERROR_GNR_KEY_VAL_ID);

        output.put("respKind", "redirect");
        output.put("destination", "myPage.html?errMsgKey=" + errMsgKey);

        return writeAsString(output);
    }

    private String authenticate(String mailAddress, String password) {

        List<LinkedHashMap<String, String>> accountRows =
                recordQueryService.select(ACCOUNT_SQL, List.of(mailAddress));
        if (accountRows.size() != 1) {
            return null;
        }

        // TODO ハッシュ化した値を比較する
        if (!password.equals(accountRows.get(0).get("PASSWORD"))) {
            return null;
        }

        return accountRows.get(0).get("ACCNT_ID");
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
```

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.LoginServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/service/LoginService.java \
        src/test/java/com/freedom/remainz_v2/web/service/LoginServiceTest.java
git commit -m "issue #11: LoginServiceを新設

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 6: CreateHtmlServiceの修正(errMsgKeyデフォルト化・respKind/destination上書き防止)

**Files:**
- Modify: `src/main/java/com/freedom/remainz_v2/web/service/CreateHtmlService.java:59-72`
- Modify: `src/test/java/com/freedom/remainz_v2/web/service/CreateHtmlServiceTest.java`

**Interfaces:**
- Consumes: 変更なし(既存の`RecordQueryService`/`ObjectMapper`/`MsgUtil`)。
- Produces: `CreateHtmlService.execute(String contextJson): String`の挙動変更のみ(シグネチャ変更なし)。
  - `contextJson`に`errMsgKey`が無い/空文字の場合、内部で`"0"`をデフォルト設定してからパーツ項目のSQLを実行する。
  - `contextJson`に既に`respKind`/`destination`が存在する場合(空文字でない場合)、それらの値を出力にそのまま引き継ぎ、`HTML_PAGE`由来の値で上書きしない。

- [ ] **Step 1: 失敗するテストを追加する**

`src/test/java/com/freedom/remainz_v2/web/service/CreateHtmlServiceTest.java`の末尾(既存の最後の`@Test`メソッドの後、クラスの閉じ括弧の前)に以下の2テストを追加する:
```java

    @Test
    void errMsgKeyが未指定の場合は0がデフォルト設定されプレースホルダーが解決されること() {

        LinkedHashMap<String, String> row = pageRow("1000203", "1001101", "エラーメッセージ一覧領域",
                "errMsgList", "SELECT ERR_MSG FROM ERR_MSG WHERE ERR_MSG_ID = #{errMsgKey}");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/remainz-v2/service/myPage.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));
        when(recordQueryService.select(eq("SELECT ERR_MSG FROM ERR_MSG WHERE ERR_MSG_ID = 0")))
                .thenReturn(new ArrayList<>());

        String result = createHtmlService.execute(
                "{\"requestKind\":\"GET\",\"requestUri\":\"/remainz-v2/service/myPage.html\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("htmlPage").get(0).path("items").get(0).path("records")).isEmpty();
    }

    @Test
    void respKindとdestinationがコンテキストに既に存在する場合は上書きしないこと() {

        LinkedHashMap<String, String> row =
                pageRow("1000201", "1000001", "システム名", "systemName",
                        "SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'");
        row.put("RESP_KIND_POST", "redirect");
        row.put("DESTINATION_POST", "myPage.html");

        when(recordQueryService.select(eq(PAGE_SQL), eq(List.of("/remainz-v2/service/myPage.html"))))
                .thenReturn(new ArrayList<>(List.of(row)));

        LinkedHashMap<String, String> systemNameRecord = new LinkedHashMap<>();
        systemNameRecord.put("GNR_VAL", "Remainz");
        when(recordQueryService.select(eq("SELECT GNR_VAL FROM GNR_KEY_VAL WHERE GNR_KEY = 'systemName'")))
                .thenReturn(new ArrayList<>(List.of(systemNameRecord)));

        String result = createHtmlService.execute(
                "{\"requestKind\":\"POST\",\"requestUri\":\"/remainz-v2/service/myPage.html\","
                        + "\"respKind\":\"redirect\",\"destination\":\"myPage.html?errMsgKey=5\"}");

        JsonNode node = JsonMapper.builder().build().readTree(result);
        assertThat(node.path("respKind").asString()).isEqualTo("redirect");
        assertThat(node.path("destination").asString()).isEqualTo("myPage.html?errMsgKey=5");
    }
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.CreateHtmlServiceTest"`
Expected: FAIL (新規2テストが失敗。1つ目はSQLプレースホルダー`#{errMsgKey}`が未解決のまま残り、モックしたSQL文字列と一致しないため空リストが得られない。2つ目は`respKind`/`destination`が`RESP_KIND_POST`/`DESTINATION_POST`の値で上書きされ`"myPage.html?errMsgKey=5"`ではなく`"myPage.html"`になるため)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/service/CreateHtmlService.java`の`execute`メソッドを以下のように変更する(既存の行59-72を置き換える):

変更前:
```java
        ObjectNode context = readAsObjectNode(contextJson);

        String requestUri = context.path("requestUri").asString("");
        String requestKind = context.path("requestKind").asString("");

        List<LinkedHashMap<String, String>> pageRows = recordQueryService.select(PAGE_SQL, List.of(requestUri));
        if (pageRows.isEmpty()) {
            throw new ApplicationInternalException(msg.get("msg.err.web.pageNotFound", requestUri));
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.set("htmlPage", buildHtmlPage(pageRows, context));
        output.put("respKind", pageRows.get(0).get("RESP_KIND_" + requestKind));
        output.put("destination", VariablePlaceholderResolver.resolve(
                pageRows.get(0).get("DESTINATION_" + requestKind), context, msg));

        return writeAsString(output);
```

変更後:
```java
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
```

**補足**: `errMsgKey`のデフォルト化は、移植元`CreateHtmlService`が`errMsgKey`未指定時に`"0"`をデフォルトとする挙動に合わせるものです。`respKind`/`destination`のガードは、`LoginService`(Task 5)がログイン失敗時にセットしたリダイレクト情報を、後続の`CreateHtmlService`実行(`SCR_ELM`の並び順、Task 7参照)が上書きしてしまう問題を防ぐためのものです(`ScriptExecutionService`が各サービスの出力を`context.setAll(...)`で無条件マージするため、移植元の`output.putStringIfNotExists(...)`に相当する挙動をこの箇所で担保する必要があります)。

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.service.CreateHtmlServiceTest"`
Expected: PASS (既存3テスト + 新規2テストの計5テスト)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/service/CreateHtmlService.java \
        src/test/java/com/freedom/remainz_v2/web/service/CreateHtmlServiceTest.java
git commit -m "issue #11: CreateHtmlServiceにerrMsgKeyデフォルト化とrespKind/destination上書き防止ガードを追加

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 7: RemainzV2Controllerにマイページの GET/POST マッピングを追加

**Files:**
- Modify: `src/main/java/com/freedom/remainz_v2/web/controller/RemainzV2Controller.java:1-49`
- Modify: `src/test/java/com/freedom/remainz_v2/web/controller/RemainzV2ControllerTest.java`

**Interfaces:**
- Consumes: 既存の`RequestHandlingService.execute(String contextJson): String`、既存のprivateメソッド`handleRequest(HttpServletRequest, String requestKind, Model)`。
- Produces: `GET /remainz-v2/service/myPage.html`、`POST /remainz-v2/service/myPage.html`のエンドポイント。

- [ ] **Step 1: 失敗するテストを追加する**

`src/test/java/com/freedom/remainz_v2/web/controller/RemainzV2ControllerTest.java`のimportに以下を追加する:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
```
既存テストメソッドの後に以下を追加する:
```java

    @Test
    void マイページのGETリクエストで応答種別forwardの場合はビュー名が拡張子無しで解決されること() throws Exception {

        when(requestHandlingService.execute(anyString())).thenReturn(
                "{\"respKind\":\"forward\",\"destination\":\"10000_contents.html\","
                        + "\"htmlPage\":[{\"partsInPageId\":\"1000201\",\"items\":[]}]}");

        mockMvc.perform(get("/remainz-v2/service/myPage.html"))
                .andExpect(status().isOk())
                .andExpect(view().name("10000_contents"));
    }

    @Test
    void マイページのPOSTリクエストで応答種別redirectの場合はredirectプレフィックス付きのビュー名が返却されること()
            throws Exception {

        when(requestHandlingService.execute(anyString()))
                .thenReturn("{\"respKind\":\"redirect\",\"destination\":\"myPage.html?errMsgKey=5\"}");

        mockMvc.perform(post("/remainz-v2/service/myPage.html")
                        .param("MAIL_ADDRESS", "wrong@account.com")
                        .param("PASSWORD", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:myPage.html?errMsgKey=5"));
    }
```

- [ ] **Step 2: テストを実行し、失敗することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.controller.RemainzV2ControllerTest"`
Expected: FAIL (`/remainz-v2/service/myPage.html`に対する`@GetMapping`/`@PostMapping`が存在しないため404)

- [ ] **Step 3: 最小限の実装を書く**

`src/main/java/com/freedom/remainz_v2/web/controller/RemainzV2Controller.java`のimportに以下を追加する:
```java
import org.springframework.web.bind.annotation.PostMapping;
```
`getTop`メソッドの直後に以下を追加する:
```java

    @GetMapping("/remainz-v2/service/myPage.html")
    public String getMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "GET", model);
    }

    @PostMapping("/remainz-v2/service/myPage.html")
    public String postMyPage(HttpServletRequest request, Model model) {
        return handleRequest(request, "POST", model);
    }
```

- [ ] **Step 4: テストを実行し、成功することを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.controller.RemainzV2ControllerTest"`
Expected: PASS (既存2テスト + 新規2テストの計4テスト)

- [ ] **Step 5: コミットする**

```bash
git add src/main/java/com/freedom/remainz_v2/web/controller/RemainzV2Controller.java \
        src/test/java/com/freedom/remainz_v2/web/controller/RemainzV2ControllerTest.java
git commit -m "issue #11: RemainzV2Controllerにマイページ(myPage.html)のGET/POSTマッピングを追加

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 8: SCR_ELMデータのサービスクラス名更新とSQL再生成

**Files:**
- Modify: `src/main/resources/db/data/SCR_ELM.txt`
- Modify (generated): `src/main/resources/db/sql/INSERT_SCR_ELM.sql`

**Interfaces:**
- Consumes: `DbSchemaSqlGeneratorRealDataTest`(既存のSQL生成テスト)。
- Produces: `SCR_ELM`テーブルの`SERVICE_NAME`列が、新設した`com.freedom.remainz_v2.web.service.LoginService`/`GetAccountService`/`CreateHtmlService`(Task 4-6で実装済み)を指すようになる。

- [ ] **Step 1: SCR_ELM.txtを編集する**

`src/main/resources/db/data/SCR_ELM.txt`の以下5行を置き換える(タブ区切り。`VERSION`列を`1`→`2`、`UPDATED_AT`列を`2026-07-21 00:00:00`に変更し、`SERVICE_NAME`列を新パッケージへ変更する。`CREATED_BY`/`CREATED_AT`は変更しない):

変更前:
```
1100201	com.remainz.web.service.web.GetAccountService			1100201	1	1	0	data_loader	2024-07-07 00:00:00	data_loader	2024-07-07 00:00:00
1100202	com.remainz.web.service.web.CreateHtmlService			1100201	2	1	0	data_loader	2024-07-07 00:00:00	data_loader	2024-07-07 00:00:00
1100251	com.remainz.web.service.web.LoginService			1100251	1	1	0	data_loader	2024-07-07 00:00:00	data_loader	2024-07-07 00:00:00
1100252	com.remainz.web.service.web.GetAccountService			1100251	2	1	0	data_loader	2024-07-07 00:00:00	data_loader	2024-07-07 00:00:00
1100253	com.remainz.web.service.web.CreateHtmlService			1100251	3	1	0	data_loader	2024-07-07 00:00:00	data_loader	2024-07-07 00:00:00
```

変更後:
```
1100201	com.freedom.remainz_v2.web.service.GetAccountService			1100201	1	2	0	data_loader	2024-07-07 00:00:00	data_loader	2026-07-21 00:00:00
1100202	com.freedom.remainz_v2.web.service.CreateHtmlService			1100201	2	2	0	data_loader	2024-07-07 00:00:00	data_loader	2026-07-21 00:00:00
1100251	com.freedom.remainz_v2.web.service.LoginService			1100251	1	2	0	data_loader	2024-07-07 00:00:00	data_loader	2026-07-21 00:00:00
1100252	com.freedom.remainz_v2.web.service.GetAccountService			1100251	2	2	0	data_loader	2024-07-07 00:00:00	data_loader	2026-07-21 00:00:00
1100253	com.freedom.remainz_v2.web.service.CreateHtmlService			1100251	3	2	0	data_loader	2024-07-07 00:00:00	data_loader	2026-07-21 00:00:00
```

- [ ] **Step 2: SQLを再生成する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.common.db.DbSchemaSqlGeneratorRealDataTest"`
Expected: PASS。`src/main/resources/db/sql/INSERT_SCR_ELM.sql`が更新される。

- [ ] **Step 3: 差分を確認する**

Run: `git diff --stat src/main/resources/db/sql/INSERT_SCR_ELM.sql`
Expected: `INSERT_SCR_ELM.sql`のみ差分があり、対象5レコードの`SERVICE_NAME`/`VERSION`/`UPDATED_AT`のみ変わっていること。

- [ ] **Step 4: コミットする**

```bash
git add src/main/resources/db/data/SCR_ELM.txt src/main/resources/db/sql/INSERT_SCR_ELM.sql
git commit -m "issue #11: SCR_ELMのマイページ関連レコードのサービスクラス名をcom.freedom.remainz_v2へ更新

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 9: 静的アセット(Bootstrap/CSS/JS/favicon)の配置

**Files:**
- Create: `src/main/resources/static/css/bootstrap.min.css`
- Create: `src/main/resources/static/css/rwstyle.css`
- Create: `src/main/resources/static/js/bootstrap.bundle.min.js`
- Create: `src/main/resources/static/js/rwscript.js`
- Create: `src/main/resources/static/img/favicon.png`

**Interfaces:**
- Consumes: なし(静的ファイルのコピーのみ)。
- Produces: Spring Bootのデフォルト静的リソース配信により、`/css/bootstrap.min.css`等のパスでアクセス可能になる(Task 10のテンプレートから参照される)。

- [ ] **Step 1: ディレクトリを作成し、移植元からファイルをコピーする**

Run:
```bash
mkdir -p src/main/resources/static/css src/main/resources/static/js src/main/resources/static/img
cp /home/develop/remainz/src/main/webapp/css/bootstrap.min.css src/main/resources/static/css/bootstrap.min.css
cp /home/develop/remainz/src/main/webapp/css/rwstyle.css src/main/resources/static/css/rwstyle.css
cp /home/develop/remainz/src/main/webapp/js/bootstrap.bundle.min.js src/main/resources/static/js/bootstrap.bundle.min.js
cp /home/develop/remainz/src/main/webapp/js/rwscript.js src/main/resources/static/js/rwscript.js
cp /home/develop/remainz/src/main/webapp/img/favicon.png src/main/resources/static/img/favicon.png
```

- [ ] **Step 2: コピーされたことを確認する**

Run: `ls -la src/main/resources/static/css src/main/resources/static/js src/main/resources/static/img`
Expected: 5ファイルすべてが存在し、サイズが0バイトでないこと。

- [ ] **Step 3: コミットする**

```bash
git add src/main/resources/static
git commit -m "issue #11: Bootstrap/CSS/JS/faviconの静的アセットを配置

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 10: Thymeleafフラグメント(ヘッダ/ログイン/リンク一覧/エラーメッセージ一覧)の追加と10000_contents.htmlの書き換え

**Files:**
- Create: `src/main/resources/templates/parts/10010_header.html`
- Create: `src/main/resources/templates/parts/common/20010_commonHeader.html`
- Create: `src/main/resources/templates/parts/10030_login.html`
- Create: `src/main/resources/templates/parts/common/20030_commonLogin.html`
- Create: `src/main/resources/templates/parts/10040_linkList.html`
- Create: `src/main/resources/templates/parts/common/20040_commonLinkList.html`
- Create: `src/main/resources/templates/parts/10120_errMsgList.html`
- Create: `src/main/resources/templates/parts/common/20120_commonErrMsgList.html`
- Modify: `src/main/resources/templates/10000_contents.html`(全面書き換え)

**Interfaces:**
- Consumes: `AuthUtil.hasReadAuth`/`hasEditAuth`(Task 1)、`HtmlPageItemUtil.findRecords`(Task 2)、Model属性`htmlPage`/`account`/`authList`(`RemainzV2Controller.populateModel`により設定済み、変更不要)。
- Produces: `myPage.html`/`top.html`双方から共有される画面テンプレート一式。本タスクにテストコード追加は無い(Thymeleafテンプレートの単体テストは本プロジェクトに前例が無いため、既存の`RemainzV2ControllerTest`(Task 7で拡張済み)のビュー名解決テストと、Step 4の手動ビルド確認で十分と判断する)。

- [ ] **Step 1: ヘッダーのラッパーフラグメントを作成する**

`src/main/resources/templates/parts/10010_header.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="wrapper(part)"
         th:if="${part.htmlPartsId == '1000001'
                 and T(com.freedom.remainz_v2.web.util.AuthUtil).hasReadAuth('1000001', authList)}">
        <div th:replace="~{parts/common/20010_commonHeader :: body}"></div>
    </div>
</body>
</html>
```

- [ ] **Step 2: ヘッダー本体フラグメントを作成する**

`src/main/resources/templates/parts/common/20010_commonHeader.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <nav th:fragment="body" class="navbar navbar-expand-md navbar-dark bg-secondary mt-2 mb-2">
        <a class="navbar-brand ms-2" href="#">
            <em th:text="${T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'systemName').get(0).GNR_VAL}">Remainz</em>
        </a>
        <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarMenu">
            <span class="navbar-toggler-icon"></span>
        </button>
        <div class="collapse navbar-collapse" id="navbarMenu">
            <ul class="navbar-nav ms-auto">
                <li class="nav-item"
                    th:each="urlLink : ${T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'urlLink')}"
                    th:if="${urlLink.URI_PATTERN != '/remainz-v2/service/error.html'}">
                    <a class="nav-link btn btn-outline-primary ms-2 me-2" th:href="${urlLink.URI_PATTERN}"
                       th:text="${urlLink.PAGE_NAME}">Link</a>
                </li>
                <li class="nav-item"><a class="nav-link" href="#" th:text="${account.get(0).ACCOUNT_NAME}">ゲスト</a></li>
            </ul>
        </div>
    </nav>
</body>
</html>
```

- [ ] **Step 3: ログインフォームのラッパー・本体フラグメントを作成する**

`src/main/resources/templates/parts/10030_login.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="wrapper(part)"
         th:if="${part.htmlPartsId == '1000201'
                 and T(com.freedom.remainz_v2.web.util.AuthUtil).hasEditAuth('1000201', authList)}">
        <div th:replace="~{parts/common/20030_commonLogin :: body}"></div>
    </div>
</body>
</html>
```

`src/main/resources/templates/parts/common/20030_commonLogin.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="body" th:if="${account.get(0).ACCNT_ID == '1000001'}" class="1000201">
        <table>
            <tbody>
                <tr>
                    <td class="px-4 py-2">メールアドレス</td>
                    <td class="px-4 py-2"><input id="MAIL_ADDRESS" name="MAIL_ADDRESS" value="grandmaster@account.com"></td>
                </tr>
                <tr>
                    <td class="px-4 py-2">パスワード</td>
                    <td class="px-4 py-2"><input id="PASSWORD" type="password" name="PASSWORD" value="password"></td>
                </tr>
            </tbody>
        </table>
        <button type="button" class="btn btn-primary px-4 py-2" onclick="submitMainForm()">サインイン</button>
    </div>
</body>
</html>
```

- [ ] **Step 4: リンク一覧のラッパー・本体フラグメントを作成する**

`src/main/resources/templates/parts/10040_linkList.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="wrapper(part)"
         th:if="${part.htmlPartsId == '1000301'
                 and T(com.freedom.remainz_v2.web.util.AuthUtil).hasEditAuth('1000301', authList)}">
        <div th:replace="~{parts/common/20040_commonLinkList :: body}"></div>
    </div>
</body>
</html>
```

`src/main/resources/templates/parts/common/20040_commonLinkList.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="body" th:if="${account.get(0).ACCNT_ID != '1000001'}" class="1000301">
        <div class="row m-2 gy-2">
            <a th:each="link : ${T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'linkList')}"
               th:if="${link.URI_PATTERN != '/remainz-v2/service/error.html'}"
               class="btn btn-primary w-100 px-4 py-2"
               th:href="${link.IS_POST == '0'} ? ${link.URI_PATTERN} : 'javascript:void(0);'"
               th:onclick="${link.IS_POST == '0'} ? null : 'submitMainForm()'"
               th:text="${link.LNK_NAME}">Link</a>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 5: エラーメッセージ一覧のラッパー・本体フラグメントを作成する**

`src/main/resources/templates/parts/10120_errMsgList.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="wrapper(part)"
         th:if="${part.htmlPartsId == '1001101'
                 and T(com.freedom.remainz_v2.web.util.AuthUtil).hasReadAuth('1001101', authList)}">
        <div th:replace="~{parts/common/20120_commonErrMsgList :: body}"></div>
    </div>
</body>
</html>
```

`src/main/resources/templates/parts/common/20120_commonErrMsgList.html`:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
    <div th:fragment="body" class="1001101">
        <div class="p-2"
             th:if="${not #lists.isEmpty(T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'errMsgList'))}">
            <div th:each="errMsg : ${T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'errMsgList')}">
                <span style="color:red;" th:text="${errMsg.ERR_MSG}">Error</span>
            </div>
        </div>
    </div>
</body>
</html>
```

- [ ] **Step 6: エントリテンプレート(10000_contents.html)を書き換える**

`src/main/resources/templates/10000_contents.html`の全内容を以下に置き換える:
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">

<head>
    <link rel="stylesheet" href="/css/bootstrap.min.css">
    <link rel="stylesheet" type="text/css" href="/css/rwstyle.css">
    <link rel="icon" type="image/png" href="/img/favicon.png">
    <title th:text="${T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, 'systemName').get(0).GNR_VAL}">Remainz</title>
</head>

<body>
    <div id="mainArea" class="container">
        <form id="mainForm" method="POST">
            <div th:each="part : ${htmlPage}">
                <div th:replace="~{parts/10010_header :: wrapper(part=${part})}"></div>
                <div th:replace="~{parts/10030_login :: wrapper(part=${part})}"></div>
                <div th:replace="~{parts/10040_linkList :: wrapper(part=${part})}"></div>
                <div th:replace="~{parts/10120_errMsgList :: wrapper(part=${part})}"></div>
            </div>
        </form>
    </div>
    <script src="/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="/js/rwscript.js"></script>
</body>

</html>
```

**補足**: `10000_contents.html`はTOP/マイページ双方から共有されるエントリテンプレートです(移植元と同じ)。TOPページの`htmlPage`には`HTML_PARTS_ID`が`1000001`(システム名)/`1000002`(共通ヘッダ、未使用)の2パートのみが含まれるため、4つの`th:replace`のうち`10010_header`のみが実際に描画されます(`th:if`の条件に一致しないラッパーは何も出力しません)。

- [ ] **Step 7: アプリケーションが起動しテンプレートがエラー無く解決されることを確認する**

Run: `./gradlew test --tests "com.freedom.remainz_v2.RemainzV2ApplicationTests"`
Expected: PASS (`contextLoads`が通ることを確認。この時点でテンプレートの構文エラー(閉じタグ不整合、フラグメント参照ミス等)があればコンテキスト起動失敗として検出される)

Run: `./gradlew test --tests "com.freedom.remainz_v2.web.controller.RemainzV2ControllerTest"`
Expected: PASS (Task 7で追加した4テストを含め、すべて成功すること)

- [ ] **Step 8: コミットする**

```bash
git add src/main/resources/templates
git commit -m "issue #11: マイページ用Thymeleafフラグメント(ヘッダ/ログイン/リンク一覧/エラーメッセージ一覧)を追加し10000_contents.htmlを書き換え

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 11: 今後の画面移植issue向けの一般手順を`.github/copilot-instructions.md`へ追記

**Files:**
- Modify: `.github/copilot-instructions.md`

**Interfaces:**
- Consumes: なし(ドキュメントのみ)。
- Produces: 今後の画面移植issueに着手する際の一般的な移植手順の記述。

- [ ] **Step 1: 移植手順のセクションを追記する**

`.github/copilot-instructions.md`の「移植元プロジェクトについて」セクションの末尾に、以下を追記する:
```markdown

### 画面移植(JSP→Thymeleaf)issue対応の一般手順

過去の画面移植issue(#9, #11)を踏まえた、今後の画面移植issue向けの一般的な手順は次の通りです。

1. 移植元JSPの`10xxx`(権限確認ラッパー)/`common/20xxx`(画面パーツ本体)のペアを、対象画面について
   洗い出す(`/home/develop/remainz/src/main/webapp/WEB-INF/jsp/`配下)。
2. 対応するThymeleafフラグメントを`src/main/resources/templates/parts/`(ラッパー)・
   `src/main/resources/templates/parts/common/`(本体)配下に、同じファイル番号(`10xxx`/`20xxx`)で
   作成する。権限確認は`T(com.freedom.remainz_v2.web.util.AuthUtil).hasReadAuth(...)`/
   `hasEditAuth(...)`をラッパーの`th:if`で呼び出す。パート横断で画面表示項目を参照する必要が
   ある場合は`T(com.freedom.remainz_v2.web.util.HtmlPageItemUtil).findRecords(htmlPage, itemKey)`
   を使う。
3. 必要なDBデータ(`SCR_ELM.SERVICE_NAME`等)を`com.remainz.*`から`com.freedom.remainz_v2.*`へ
   更新し(`VERSION`を1増やし`UPDATED_AT`を作業日に更新)、
   `DbSchemaSqlGeneratorRealDataTest`を実行してSQLを再生成する。
4. 必要なバックエンドサービス(`ScriptElementService`実装)を移植する。例外方針
   (`BusinessRuleViolationException`/`ApplicationInternalException`)・JSON入出力方針
   (`tools.jackson.*`、`MsgUtil`経由のログメッセージ)は本プロジェクトの規約に合わせて書き直す。
5. 各サービス・ユーティリティクラスに、Mockitoベースの対応するテストクラスを追加する
   (`@ExtendWith(MockitoExtension.class)`、DBアクセスは`RecordQueryService`をモック化)。
```

- [ ] **Step 2: コミットする**

```bash
git add .github/copilot-instructions.md
git commit -m "issue #11: 画面移植issue向けの一般手順をcopilot-instructions.mdへ追記

Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>"
```

---

### Task 12: 全体テストとビルドの最終確認

**Files:**
- なし(検証のみ)

**Interfaces:**
- Consumes: 全タスクの成果物。
- Produces: なし(検証結果のみ)。

- [ ] **Step 1: 全テストを実行する**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL、全テストがPASSすること。

- [ ] **Step 2: ビルドを実行する**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 問題があれば修正し、無ければ完了とする**

すべて成功した場合、追加のコミットは不要(各タスクで既にコミット済み)。問題が見つかった場合は
該当タスクに戻って修正し、再度本タスクのStep 1から実行する。
