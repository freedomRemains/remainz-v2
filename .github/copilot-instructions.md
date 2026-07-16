# Copilot Instructions for remainz-v2

## プロジェクトの目的

`remainz-v2` は「**ローコードWebアプリ構築ツール**」です。画面遷移や業務ロジックの呼び出しを機能ごとにハードコードするのではなく、画面構成・ルーティング・権限の構造を**DBレコード**として設計し、Javaコード側は汎用的にそのレコードを解釈するエンジンとして実装します。全体のデータモデルは
`documents/design/2000002_model_design.md` を参照してください。ルーティングの流れは次の通りです。

```
URI_PATTERN（受信URL）
  -> HTML_PAGE（HTTPメソッドごとに、どのSCRを実行するか／応答種別／遷移先を定義）
    -> SCR（スクリプト） -> SCR_ELM（サービスクラスの実行順リスト。例: com.remainz.web.service.web.GetAccountService）
      -> 各サービスはJSONを入出力とし、あるサービスの出力が次のサービスの入力になる
        -> HTML_PARTS / PARTS_IN_PAGE（再利用可能な画面パーツを組み合わせて最終的な画面を構成）
```

権限まわりもデータ駆動です。`ACCNT`（アカウント）と `APROLE`（ロール）は
`APROLE_IN_ACCNT` で紐づき、画面アクセス制御は `REQUIRE_APROLE`、画面パーツ単位の
read/edit 制御は `HTML_PARTS_IN_APROLE` で管理し、`AuthUtil.hasEditAuth(...)` /
`AuthUtil.hasReadAuth(...)` で判定します。同じ思想の以前のServlet+JSP実装が
移植元プロジェクト「remainz」にあり、本プロジェクト（Spring Boot版）へ移植する際の
参考になります（移植元の参照方法は後述「移植元プロジェクトについて」参照）。

現状のソース（`src/main/java/com/freedom/remainz_v2/...`）はごく初期の骨組み
（Controller1つ、Thymeleafテンプレート1つ）であり、上記モデルの大部分はまだ設計段階で、
実装はこれからです。パッケージ名が `remainz_v2`（アンダースコア区切り）であることに注意
してください。`-` はJavaのパッケージ名として使えないためです（`HELP.md` 参照）。

## 移植元プロジェクトについて

`remainz-v2` は、サーブレット＋JSPで実装された旧プロジェクト「remainz」を、
Spring Boot＋Thymeleafへ乗せ換え、ECSコンテナなどにデプロイしやすくすることを
目的とした移植プロジェクトです。移植元は既に動作確認済みの実物資材であり、今後
頻繁に移植依頼が発生します。

- 移植元の参照先は、インターネット上のGitHub URL（`github.com/freedomRemains/remainz`）
  ではなく、**ローカルクローン `/home/develop/remainz`（`develop`ブランチ）を参照する**
  こと。ローカルの方がファイル横断のgrep検索や全文参照がしやすく、正確・高速なため。
- 移植元の開発は停止しているため、ローカル資材とGitHub上の資材は実質的に同一だが、
  念のため作業前に最新化（`git pull`等）しておくと安心。
- 移植依頼時は対象のクラス名・パッケージ・機能単位を具体的に指定すると、探索の手間が
  減り精度が上がる。

## パッケージ構成とコントローラの方針（`documents/design/2000001_base_design.md` 参照）

- `remainz-v2` のパッケージは `com.freedom.remainz_v2` 配下に `common`（DB非依存の共通資材:
  `db`, `exception`, `service`, `util`）と `web`（Webアプリ固有資材: `controller`,
  `exception`, `service`, `util`）を配置する。移植元「remainz」にあった `common/param`
  （`GenericParam`）と `web/servlet` は廃止し、`web/controller` を新設する。
- **コントローラは `RemainzV2Controller` 1つのみ**とする。`URI_PATTERN` テーブルの値を
  `@GetMapping`/`@PostMapping` に、`HTML_PAGE` テーブルで `SCR_ID_GET`/`SCR_ID_POST` 等が
  0でないものをコントローラのメソッドとして記述する。各メソッドはDBレコードに基づいて
  処理するサービスを呼び出すだけとし、移植元の
  `com.remainz.web.servlet.ServiceControlServlet#controllService` を参考にする。
- 仮実装の `RemainzV2Controller`（現状 `controller/` パッケージ直下）は上記構成に従って
  いないため、実装を進める際は配置を見直すこと。

## 例外処理の方針（`documents/design/2000001_base_design.md` 参照）

- 業務的なエラーは `BusinessRuleViolationException`、それ以外のシステム的なエラーは
  `ApplicationInternalException` をスローする（いずれも移植元 `com.remainz.common.exception`
  配下のクラスが元）。入力JSONの必須パラメータ欠如は前者、IOException等の技術的失敗は後者。
- 両例外は **RuntimeException派生**とし、`throws` を書かずに済むようにする。
  チェック例外をキャッチしてまで包み直す必要はなく、`throws` が必須な例外のみ
  catchしてrethrowする。グローバル例外ハンドラは
  `BusinessRuleViolationException` / `ApplicationInternalException` / `Exception`（その他全て）
  の3種類に絞る。

## スクリプト処理とパラメータの渡し方（`documents/design/2000001_base_design.md` 参照）

- `SCR_ELM.SERVICE_NAME` にJavaクラスの完全修飾名を格納し、任意の業務ロジックを任意の順で
  呼び出す「スクリプト」の仕組みは踏襲する。
- 移植元では入出力に `GenericParam` クラスを使っていたが、`remainz-v2` では **String型の
  JSON文字列**に置き換え、`ObjectMapper` で必要なパラメータを取得する。ある業務ロジックの
  出力JSONが次の業務ロジックの入力JSONになる点は変わらない。

## DBアクセスの方針（`documents/design/2000001_base_design.md` 参照）

- 移植元の `com.remainz.common.db.GenericDb` に相当する仕組みは、変更せずそのまま採用する
  （MyBatisやJPAへの移植は複雑化を招くため行わない）。
- SELECT結果は `ArrayList<LinkedHashMap<String, String>>`（1レコード=`LinkedHashMap`、
  カラム順序はSELECT記述順を維持）で返却し、値は元の型（INT/DATETIMEなど）に関わらず
  一律文字列として扱う。画面表示用データは全て文字列という割り切りに基づく。
- `DataSource` をインジェクションし `PreparedStatement`/`ResultSet` を使う実装とする。
  トランザクションはSpringBootに任せ、commitは1箇所に集約する（複数箇所でcommitする
  コードは避ける。移植元では `ServiceControlServlet` の1箇所のみでcommitしていた）。
- SQLiteはh2/MySQLと書き方が異なる可能性があるため、差異が出た場合はSQLite向けの
  パッケージを新設し、そこに専用のSQL生成/アクセスコードを配置する（移植元の
  `DbInterface`/`H2Db`/`MysqlDb` のようにDB種別ごとに実装を切り替えられる構造を踏襲）。

## DB定義・DBデータ資材の生成（`documents/design/2000001_base_design.md` 参照）

- DB定義は `src/main/resources/db/TBL_DEF.txt` で管理する（移植元は
  `src/test/resources/service/script/dbmng/h2/20_dbdata/TBL_DEF.txt`）。不要なテーブルを
  除き、基本的にはそのまま流用する。各テーブルのDROP/CREATE SQLはこのファイルから生成する
  （移植元の `GetTableCreateSqlService`/`GetTableDropSqlService` が該当）。
- DBデータは `src/main/resources/db` 配下（1テーブル1ファイル）で管理し、INSERT/SELECT SQL
  を生成する（移植元の `GetTableInsertSqlService`/`GetTableSelectSqlService` が該当、
  移植元データは `src/test/resources/service/script/dbmng/h2/20_dbdata/10_authorized` 配下）。
- これらSQL生成コードを移植する際も、例外処理・入出力（JSON文字列化）は上記の新方針に
  合わせて書き直す。
- `TBL_DEF` テーブルはDB内のテーブル定義自体を保持する特殊テーブルで、`VERSION` 以下の
  定型カラムを持たない。`FOREIGN_TABLE`（外部キー先テーブル）、`DESC_FIELD`
  （サロゲートキーの意味を説明する実質的なキー項目、例: `ACCNT.ACCNT_ID` に対する
  `ACCOUNT_NAME`）などの特殊カラムを持つ。

## DBマスタデータの採番規則（`documents/design/2000001_base_design.md` 参照）

- IDは `1000001` から採番する（`1`〜`1000000` は予約領域として使用しない）。
- 基本は100番ずらし（`1000001`, `1000101`, ...）。ただし「はい/いいえ」のように意味的に
  グルーピングできるレコード群は、100番ずらしではなく連番（`1000301`, `1000302`, ...）で
  採番する。採番方法に迷う場合は必ず質問すること。
- 新規レコードは `CREATED_BY`/`UPDATED_BY` を `data_loader`、`CREATED_AT`/`UPDATED_AT` を
  作成日の `00:00:00`、`VERSION` を `1`、`IS_DELETED` を `0` とする。
- レコード変更時は `VERSION` を1増やし `UPDATED_AT` を更新日の `00:00:00` にする（単純ミス
  修正など、`VERSION` を変えない旨の明示的な指示があればそちらを優先する）。

## アーキテクチャ／スタック

- Java 21、Spring Boot（`spring-boot-starter-webmvc`, `-jdbc`, `-thymeleaf`, `-mail`, `-validation`）。
- DBアクセスは素のJDBC（`spring-boot-starter-jdbc`）であり、JPAやMyBatisではありません。
  `documents/knowledge/` 配下の一部資料はMyBatisに言及していますが、これは関連する別
  プロジェクトの知見が混在しているためで、本プロジェクトの依存関係には含まれません。
- DB: 現状はローカル・本番ともにSQLite（`org.xerial:sqlite-jdbc`、`application.yaml` の
  `jdbc:sqlite:./taskallv2.db`）。将来的に本番はMySQLへ移行予定のため、**SQLite固有の
  SQL構文は使わない**ようにしてください（`documents/design/2000001_base_design.md`）。
- ビュー層: `src/main/resources/templates/` 配下のThymeleafテンプレート。

### DBテーブルの命名規則（`documents/design/2000001_base_design.md` 参照）

- 主キーは必ず自動採番のサロゲートキーとし、物理名は `<テーブル物理名>_ID` とする。
- 外部キーの物理名は `<参照先テーブル物理名>_ID` とし、フィールド名だけで関連が分かるようにする。
- テーブル名・カラム物理名は英大文字＋アンダーバー、28文字以内。文字数を抑えるため略語使用可。
- 全テーブル共通で `VERSION`, `IS_DELETED`, `CREATED_BY`, `CREATED_AT`, `UPDATED_BY`,
  `UPDATED_AT` を付与する。

## ビルド／テスト

- ビルド: `./gradlew build`
- 全テスト実行: `./gradlew test`
- 単一テストクラスの実行: `./gradlew test --tests "com.freedom.remainz_v2.RemainzV2ApplicationTests"`
- 単一テストメソッドの実行: `./gradlew test --tests "com.freedom.remainz_v2.RemainzV2ApplicationTests.contextLoads"`
- JDK 21が必要（`JAVA_HOME` の設定必須）。Windowsでの環境変数設定例（`chcp 65001` や
  `JAVA_OPTS=-Dfile.encoding=UTF-8` など）は `documents/knowledge/build.md` 参照。
- インジェクション対象クラスが見つからない、といったテストエラーの場合は、まず
  `application.yaml` / `application.properties` の設定漏れを疑ってください。使用している
  Spring Bootの機能（datasource, mailなど）に必要な設定項目が無いとコンテキストが起動
  できません（`documents/knowledge/gradleError.md`）。

## テストの方針

- 単体テストは**Mockito**を使用し、DBを使う結合テストは基本的に行いません。A -> B -> C -> D
  のような多段の呼び出しがあっても、直下の子（例: A -> Bのテストなら B）だけをモックすること
  で、階層ごとに分割してテストできます（`documents/knowledge/junit.md`）。パターン:
  テストクラスに `@ExtendWith(MockitoExtension.class)`、協働クラスに `@Mock`、
  テスト対象クラスに `@InjectMocks` を付与し、`when(...).thenReturn(...)` でモックの
  挙動を事前設定してから、実際のメソッドの挙動をassertで検証する。
- 実装クラスには必ずペアとなるテストクラスを用意すること。テストの無い実装はNGとされます
  （`documents/design/2000003_implementation_design.md`）。

## 開発の進め方（AI駆動開発フロー）

本プロジェクトはissue -> ブランチ -> pull requestのループで、AIエージェントによって開発が
進められます（`documents/design/2000003_implementation_design.md`）。

- **issueの指定が無い実装依頼には着手しないこと。** issueのURLが示されていない場合は
  「実装依頼にはissueが必要です」と回答し、実装作業を行わない。
- 実装作業を開始する前に、**superpowers**（https://github.com/obra/superpowers）が
  有効かどうかを確認すること。`using-superpowers`, `brainstorming`,
  `test-driven-development`, `systematic-debugging`, `writing-plans` などsuperpowers
  プラグイン由来のスキル群が利用可能なスキルとして読み込まれているかをチェックする。
- ブランチ名は `feature/<issue番号>`（例: issue #1 なら `feature/1`）とする。
- 実装には必ず対応するテストを付けること（上記参照）。
- pull requestは `feature/<issue番号>` から `develop` ブランチ宛てに作成する（`main` ではない）。
- 実装完了時はpull requestのURLを成果物として報告する。
- issueの記載内容に不明点がある、論理的に矛盾している、といった場合は**自律的に仕様を
  訂正・補完せず、必ず質問すること。**
- pull requestへの指摘対応時も、該当のコメント内容を確認し、実装・テスト双方を修正する。
  この場合も不明点があれば必ず質問すること。

## ドキュメントの管理方法

- `documents/design/` — 論理設計・アーキテクチャ設計資料（`2000xxx_*.md` の連番）。
- `documents/knowledge/` — Gradle、JUnit、ログ、バッチ、MinIO、Redisセッションなど、
  個別技術トピックに関するナレッジ資料。`README.md` からリンクされている。
- `documents/prompts/` — AIに対して行った問い合わせ内容（プロンプト）の保存先。
- `documents/rules/` — AIの回答をルールとして保存する場所。一部ファイルは
  `[AI回答のマークダウンを貼り付けて保存する]` のままの未着手プレースホルダーであり、
  内容が未整備の状態のものがある。
- ルール類の陳腐化防止のため、定期的に `documents/rules/` の内容を見直し、それに応じて
  コード・テストを更新する運用とする。
