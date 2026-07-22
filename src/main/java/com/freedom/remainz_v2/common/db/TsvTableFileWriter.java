package com.freedom.remainz_v2.common.db;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.util.MsgUtil;

/**
 * テーブルデータを、タブ区切り(TSV)形式のファイルへ書き出すクラスです。
 *
 * <p>
 * {@link TsvTableFileReader}が読み込むファイル形式(1行目ヘッダ、2行目以降が1レコード)と
 * 対になる書き込み側のクラスです。DBメンテナンス機能(DB構成取得)で、実行時のライブDBから
 * 取得したテーブル定義・テーブルデータをファイルへ退避する際に使用します。大量データを一度に
 * メモリへ保持しないよう、1レコードずつ追記できる{@link #append(Path, List)}も提供します。
 * </p>
 */
public class TsvTableFileWriter {

    private final MsgUtil msg;

    public TsvTableFileWriter() {
        this(new MsgUtil());
    }

    public TsvTableFileWriter(MsgUtil msg) {
        this.msg = msg;
    }

    /**
     * ヘッダ行を含む全レコードを、新規にTSVファイルへ書き出します。
     *
     * @param filePath 出力先ファイルパス
     * @param rows     書き出すレコードの一覧(1レコード=1マップ、キー順がそのままカラム順になる)
     */
    public void write(Path filePath, List<? extends Map<String, String>> rows) {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            if (rows.isEmpty()) {
                return;
            }
            writeHeaderIfNeeded(writer, rows.get(0));
            writeRows(writer, rows);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileWriteFailed", filePath), e);
        }
    }

    /**
     * 既存ファイルへレコードを追記します。ファイルが存在しない場合はヘッダ行から新規作成します。
     *
     * <p>
     * ライブDBのテーブルデータを一定件数(バッチ)ごとに取得しながら書き出す場合、テーブル全体を
     * 一度にメモリ上へ保持せずに済むよう、バッチ単位で本メソッドを繰り返し呼び出します。
     * </p>
     *
     * @param filePath 出力先ファイルパス
     * @param rows     追記するレコードの一覧(1バッチ分)
     */
    public void append(Path filePath, List<? extends Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return;
        }

        boolean fileExists = Files.exists(filePath);
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
                fileExists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)) {
            if (!fileExists) {
                writeHeaderIfNeeded(writer, rows.get(0));
            }
            writeRows(writer, rows);
        } catch (IOException e) {
            throw new ApplicationInternalException(msg.get("msg.err.common.db.fileWriteFailed", filePath), e);
        }
    }

    private void writeHeaderIfNeeded(BufferedWriter writer, Map<String, String> firstRow) throws IOException {
        writer.write(String.join("\t", firstRow.keySet()));
        writer.newLine();
    }

    private void writeRows(BufferedWriter writer, List<? extends Map<String, String>> rows) throws IOException {
        for (Map<String, String> row : rows) {
            writer.write(String.join("\t", row.values().stream()
                    .map(value -> value == null ? "" : value)
                    .toList()));
            writer.newLine();
        }
    }
}
