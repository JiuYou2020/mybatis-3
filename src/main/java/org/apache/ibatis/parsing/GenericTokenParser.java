/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 */
// 定义一个通用的令牌解析器
public class GenericTokenParser {

    // 开放令牌，例如 "${"
    private final String openToken;
    // 关闭令牌，例如 "}"
    private final String closeToken;
    // 用于处理找到的令牌的处理器
    private final TokenHandler handler;

    // 构造函数，接收开放令牌，关闭令牌和令牌处理器作为参数
    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    // 解析文本的方法
    public String parse(String text) {
        // 如果文本为空，直接返回空字符串
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 搜索开放令牌的位置
        int start = text.indexOf(openToken);
        // 如果没有找到开放令牌，直接返回原文本
        if (start == -1) {
            return text;
        }
        // 把文本转换为字符数组
        char[] src = text.toCharArray();
        int offset = 0;
        // 用于构建结果的字符串构建器
        final StringBuilder builder = new StringBuilder();
        // 用于构建找到的令牌的字符串构建器
        StringBuilder expression = null;
        // 当还能找到开放令牌时，执行循环
        while (start > -1) {
            // 如果开放令牌前面是反斜杠，那么这个开放令牌是转义的，需要忽略
            if (start > 0 && src[start - 1] == '\\') {
                // 添加转义的开放令牌
                builder.append(src, offset, start - offset - 1).append(openToken);
                offset = start + openToken.length();
            } else {
                // 找到开放令牌，开始搜索关闭令牌
                if (expression == null) {
                    expression = new StringBuilder();
                } else {
                    expression.setLength(0);
                }
                // 添加开放令牌前面的文本
                builder.append(src, offset, start - offset);
                offset = start + openToken.length();
                int end = text.indexOf(closeToken, offset);
                // 当还能找到关闭令牌时，执行循环
                while (end > -1) {
                    // 如果关闭令牌前面是反斜杠，那么这个关闭令牌是转义的，需要忽略
                    if (end > offset && src[end - 1] == '\\') {
                        // 添加转义的关闭令牌
                        expression.append(src, offset, end - offset - 1).append(closeToken);
                        offset = end + closeToken.length();
                        end = text.indexOf(closeToken, offset);
                    } else {
                        // 添加关闭令牌前面的令牌文本
                        expression.append(src, offset, end - offset);
                        offset = end + closeToken.length();
                        break;
                    }
                }
                if (end == -1) {
                    // 如果没有找到关闭令牌，添加剩余的文本
                    builder.append(src, start, src.length - start);
                    offset = src.length;
                } else {
                    // 添加处理后的令牌
                    builder.append(handler.handleToken(expression.toString()));
                    offset = end + closeToken.length();
                }
            }
            // 搜索下一个开放令牌
            start = text.indexOf(openToken, offset);
        }
        // 添加剩余的文本
        if (offset < src.length) {
            builder.append(src, offset, src.length - offset);
        }
        // 返回构建的字符串
        return builder.toString();
    }
}
