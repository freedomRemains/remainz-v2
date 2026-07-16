package com.freedom.remainz_v2.common.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import com.freedom.remainz_v2.common.exception.ApplicationInternalException;
import com.freedom.remainz_v2.common.exception.BusinessRuleViolationException;

/**
 * タブ区切り(TSV)形式のテーブル定義／テーブルデータファイルを読み込むクラスです。
 *
 * <p>
 * 1行目をヘッダ行、2行目以降を1レコードとして扱い、{@code ArrayList<LinkedHashMap<String, String>>}
 * （1レコード＝{@code LinkedHashMap}、ヘッダの記述順を維持）で返却します。
 * 空行はスキップします。値が列数より少ない行は、不足分を空文字列として扱います。
 * </p>
 */
public class TsvTableFileReader {

    /**
     * TSVファイルを読み込み、レコードのリストを返却します。
     *
     * <p>
     * SQL生成（開発時のファイル生成処理）など、ファイルシステム上のパスを直接扱いたい場合に使用します。
     * </p>
     *
     * @param filePath 読み込むTSVファイルのパス
     * @return 読み込んだレコードのリスト
     */
    public ArrayList<LinkedHashMap<String, String>> read(Path filePath) {

        if (!Files.exists(filePath)) {
            throw new BusinessRuleViolationException("ファイルが存在しません。filePath=" + filePath);
        }
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return read(inputStream);
        } catch (IOException e) {
            throw new ApplicationInternalException("ファイルの読み込みに失敗しました。filePath=" + filePath, e);
        }
    }

    /**
     * TSVの内容を読み込み、レコードのリストを返却します。
     *
     * <p>
     * クラスパス上のリソース（パッケージされたjar内も含む）を読み込めるよう、
     * {@link InputStream}を受け取る形にしています。
     * </p>
     *
     * @param inputStream 読み込むTSV内容の入力ストリーム（呼び出し側でクローズすること）
     * @return 読み込んだレコードのリスト
     */
    public ArrayList<LinkedHashMap<String, String>> read(InputStream inputStream) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return readRecords(reader);
        } catch (IOException e) {
            throw new ApplicationInternalException("TSVの読み込みに失敗しました。", e);
        }
    }

    private ArrayList<LinkedHashMap<String, String>> readRecords(BufferedReader reader) throws IOException {

        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new BusinessRuleViolationException("ヘッダ行が存在しません。");
        }
        String[] headers = headerLine.split("\t", -1);

        ArrayList<LinkedHashMap<String, String>> records = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            String[] values = line.split("\t", -1);
            LinkedHashMap<String, String> record = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) {
                record.put(headers[i], i < values.length ? values[i] : "");
            }
            records.add(record);
        }
        return records;
    }
}
