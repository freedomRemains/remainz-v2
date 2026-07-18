package com.freedom.remainz_v2.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

/**
 * {@code #{key}}形式のプレースホルダーを、JSONコンテキストの値で置換するユーティリティクラスです。
 *
 * <p>
 * 移植元「remainz」の{@code ScriptService#convertVariable}に相当する処理です。
 * コンテキストに対応する値が存在しない場合は、警告ログを出力したうえでプレースホルダーを
 * そのまま残します。
 * </p>
 */
public final class VariablePlaceholderResolver {

    private static final Logger logger = LoggerFactory.getLogger(VariablePlaceholderResolver.class);

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{([0-9a-zA-Z]+)\\}");

    private VariablePlaceholderResolver() {
    }

    /**
     * テンプレート文字列内の{@code #{key}}形式のプレースホルダーを、コンテキストの値で置換します。
     *
     * @param template テンプレート文字列(nullの場合はnullをそのまま返却する)
     * @param context  プレースホルダーの置換に使用するJSONコンテキスト
     * @return 置換後の文字列
     */
    public static String resolve(String template, JsonNode context) {

        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {

            String key = matcher.group(1);
            JsonNode valueNode = context.get(key);

            result.append(template, lastEnd, matcher.start());

            if (valueNode == null || valueNode.isNull() || valueNode.asString().isEmpty()) {
                logger.warn("プレースホルダーに対応する値が見つかりません。key={}", key);
                result.append(matcher.group());
            } else {
                result.append(valueNode.asString());
            }

            lastEnd = matcher.end();
        }
        result.append(template.substring(lastEnd));

        return result.toString();
    }
}
