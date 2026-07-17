# ネーミング規約

---

[READMEに戻る](../../README.md)

---

## 基本方針

- 一般的なJavaの命名規則をベースとし、本プロジェクト固有の規則（DBテーブル・カラムの
  物理名規則）は`documents/design/2000001_base_design.md`を優先する。

---

## Javaの命名規則

| 対象                       | 規則                       | 例                                             |
| -------------------------- | -------------------------- | ----------------------------------------------- |
| パッケージ名               | 全て小文字                 | `com.freedom.remainz_v2.web.service`            |
| クラス名／インターフェース | パスカルケース（大文字始まり） | `AccountService`, `DbInterface`                |
| メソッド名                 | キャメルケース、**動詞始まり** | `getAccount`, `createHtml`, `hasEditAuth`      |
| 変数名／フィールド名       | キャメルケース             | `accountId`, `htmlPartsId`                      |
| 定数（`static final`）     | 大文字＋アンダーバー       | `DEFAULT_PAGE_SIZE`, `GUEST_ACCOUNT_ID`         |
| 型パラメータ               | 大文字1文字                | `T`, `K`, `V`                                   |

- メソッド名は「何をするか」が動詞で始まるようにする。
    - 取得: `get〜`（単純な取得）、`find〜`（検索・0件もありうる場合）
    - 判定: `is〜`／`has〜`（boolean戻り値）
    - 生成: `create〜`
    - 更新: `update〜`
    - 削除: `delete〜`
- boolean型のフィールド・メソッドは`is`／`has`を先頭に付与する（例: `isDeleted`, `hasEditAuth`）。
- 単数・複数を区別する。リストを返す場合は複数形（`accountList`のような冗長な型名の
  接尾辞よりも、可能であれば`accounts`のような素直な複数形を優先するが、既存コードの
  慣習に合わせて統一する）。
- 汎用的すぎる名前（`data`, `info`, `obj`, `temp`など）は避け、具体的な意味を持つ名前にする。
- 略語を使う場合は、プロジェクト内で意味が一意に通じるものに限定する
  （DBの物理名で使われる略語は`documents/design/2000001_base_design.md`のテーブル定義例を
  参照し、対応するJavaコード側でも同じ略語・語彙を使う）。

---

## クラス種別ごとの命名

| 種別               | 命名パターン           | 例                        |
| ------------------ | ----------------------- | ------------------------- |
| Controller          | `<機能>Controller`      | `RemainzV2Controller`     |
| Service             | `<業務ロジック>Service` | `GetAccountService`, `CreateHtmlService` |
| 例外クラス          | `<内容>Exception`       | `BusinessRuleViolationException` |
| テストクラス        | `<対象クラス名>Test`    | `AccountServiceTest`      |

---

## DBまわりの命名（`documents/design/2000001_base_design.md`と対応）

- DBのテーブル名・カラム物理名は大文字＋アンダーバー区切りとする
  （例: `ACCNT_ID`, `HTML_PAGE_ID`）。これはJavaの一般的な命名規則とは別に、DB設計として
  定めた規則であり混同しないこと。
- 本プロジェクトのDBアクセス層はクエリ結果を`LinkedHashMap<String, String>`（キーは
  DBカラム物理名そのまま）で受け渡しするため、DBカラム名をキャメルケースのJavaフィールド
  に変換するマッピング処理は行わない。JSON上のキーもDBカラム物理名をそのまま使う。

---

[READMEに戻る](../../README.md)

---
