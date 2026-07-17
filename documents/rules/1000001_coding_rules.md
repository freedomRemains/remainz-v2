# コーディング規約

---

[READMEに戻る](../../README.md)

---

## 基本方針

- 「必要最小限のコード」を常に意識する（詳細は
  `documents/rules/1000021_minimum_composition.md` を参照）。
- 例外処理・DBアクセス・スクリプト呼び出しなど、本プロジェクト固有の設計方針は
  `documents/design/2000001_base_design.md` を優先する。本ルールと矛盾する場合は
  設計資料の内容を優先し、疑問があれば質問すること。

---

## パッケージ・クラス構成

- パッケージ構成は `documents/design/2000001_base_design.md`「パッケージ構成について」
  の通りとする（`common`: DB非依存の共通資材、`web`: Webアプリ固有資材）。
- 1クラス1責務とする。Controller・Service・DBアクセスの役割を混在させない。
    - Controller: リクエストの受け口。DBレコードに基づいて呼び出すサービスを決定するのみとし、
      業務ロジックを直接書かない。
    - Service: 業務ロジック本体。入出力はJSON文字列（`String`）とし、`ObjectMapper`で
      必要なパラメータを取得・設定する。
    - DBアクセス: `GenericDb`相当の共通クラス経由でのみ行う。Service内で直接JDBCコードを
      書かない。
- 依存性注入は**コンストラクタインジェクション**を使用する（フィールドインジェクション
  `@Autowired`をフィールドに直接付与するスタイルは避ける）。
- クラスやメソッドが持つ責務が肥大化してきたと感じたら、その時点で分割を検討する。
  最初から過度に細分化・共通化しない（`documents/rules/1000021_minimum_composition.md`参照）。

---

## 例外処理

- 業務的なエラーは`BusinessRuleViolationException`、システム的なエラーは
  `ApplicationInternalException`をスローする（いずれもRuntimeException派生）。
  詳細は`documents/design/2000001_base_design.md`「例外処理について」を参照。
- `throws`宣言が必要なチェック例外のみ、キャッチして上記いずれかにラップしてスローし直す。
  それ以外の箇所でtry-catchを書かない。
- 例外をもみ消す（catchして何もしない）実装は禁止。ログ出力もせずに握りつぶさない。
- グローバル例外ハンドラ（`@ControllerAdvice`等）で
  `BusinessRuleViolationException` / `ApplicationInternalException` / `Exception`
  の3種類のみをハンドリングする。業務コード側で個別に画面遷移用のtry-catchを書かない。

---

## ログ

- ログ出力にはSLF4J（`LoggerFactory.getLogger(クラス名.class)`）を使用する。
  `System.out.println`によるデバッグ出力を実装に残さない。
- ログレベルは目的に応じて使い分ける。
    - `ERROR`: システム的な異常（`ApplicationInternalException`発生時など）。
    - `WARN`: 業務的なエラー（`BusinessRuleViolationException`発生時など）。
    - `INFO`: 主要な処理の開始・終了など、運用時に追いたい情報。
    - `DEBUG`: 開発時のみ必要な詳細情報。

---

## DBアクセス（JDBC）まわりの記法

- `PreparedStatement`を使用し、SQL文字列にパラメータを直接埋め込まない（SQLインジェクション対策）。
- 検索結果は`ArrayList<LinkedHashMap<String, String>>`で受け取り、カラム値は文字列として
  扱う（`documents/design/2000001_base_design.md`「DBクエリについて」参照）。
- コミット・ロールバックは1箇所（DB制御の共通処理内）に集約し、業務ロジック側で個別に
  commit/rollbackを呼び出さない。
- SQLite固有の構文は使用しない（将来のMySQL移行を見据えるため）。

---

## コメント・Javadoc

- クラス・publicメソッドには、何をするものかが名前だけでは分かりにくい場合のみ
  Javadocを付与する。自明な内容（getter/setterなど）にはコメント不要。
- コード中のコメントは「なぜそうしているか」の説明に限定し、コードを読めば分かる内容
  （「何をしているか」の逐次説明）は書かない。

---

## Thymeleafのコーディングルール

- テンプレートには**業務ロジックを書かない**。条件分岐や計算は極力Controller/Service側で
  完了させ、テンプレートは受け取ったデータを表示するだけの薄い構成にする。
- テキスト出力は原則`th:text`を使用する（HTMLエスケープされ、XSS対策になる）。
  HTMLをそのまま埋め込む必要がある場合のみ`th:utext`を使用し、その理由をコメントで残す。
- 繰り返し表示は`th:each`、条件表示は`th:if`/`th:unless`を使用する。
- 共通のヘッダ・フッタ・ナビゲーションなど、複数画面で使うパーツは
  `th:insert`/`th:replace`でフラグメント化し、重複を避ける
  （本プロジェクトではDB上の`HTML_PARTS`/`PARTS_IN_PAGE`による部品管理の仕組みとも
  整合させること）。
- フォーム入力は`th:object`/`th:field`によるオブジェクトバインディングを使用する。
- テンプレートファイル名は、対応する`HTML_PAGE`テーブルのレコードと対応が取れる、
  分かりやすい名前とする（既存の`10000_contents.html`のような命名を踏襲する）。
- インラインJavaScript（`th:inline="javascript"`）の多用を避け、複雑なスクリプトは
  静的リソース（`src/main/resources/static/`）側に外出しする。

---

[READMEに戻る](../../README.md)

---
