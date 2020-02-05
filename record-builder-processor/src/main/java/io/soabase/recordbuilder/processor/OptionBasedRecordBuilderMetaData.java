/**
 * Copyright 2019 Jordan Zimmerman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.soabase.recordbuilder.processor;

import java.util.Map;

import io.soabase.recordbuilder.core.RecordBuilderMetaData;

public class OptionBasedRecordBuilderMetaData implements RecordBuilderMetaData {
    /**
     * @see #suffix()
     */
    public static final String OPTION_SUFFIX = "suffix";
    /**
     * @see #copyMethodName()
     */
    public static final String OPTION_COPY_METHOD_NAME = "copyMethodName";
    /**
     * @see #builderMethodName()
     */
    public static final String OPTION_BUILDER_METHOD_NAME = "builderMethodName";
    /**
     * @see #buildMethodName()
     */
    public static final String OPTION_BUILD_METHOD_NAME = "buildMethodName";
    /**
     * @see #componentsMethodName()
     */
    public static final String OPTION_COMPONENTS_METHOD_NAME = "componentsMethodName";
    /**
     * @see #fileComment()
     */
    public static final String OPTION_FILE_COMMENT = "fileComment";
    /**
     * @see #fileIndent()
     */
    public static final String OPTION_FILE_INDENT = "fileIndent";
    /**
     * @see #prefixEnclosingClassNames()
     */
    public static final String OPTION_PREFIX_ENCLOSING_CLASS_NAMES = "prefixEnclosingClassNames";

    private final String suffix;
    private final String copyMethodName;
    private final String builderMethodName;
    private final String buildMethodName;
    private final String componentsMethodName;
    private final String fileComment;
    private final String fileIndent;
    private final boolean prefixEnclosingClassNames;

    public OptionBasedRecordBuilderMetaData(Map<String, String> options) {
        suffix = options.getOrDefault(OPTION_SUFFIX, "Builder");
        builderMethodName = options.getOrDefault(OPTION_BUILDER_METHOD_NAME, "builder");
        copyMethodName = options.getOrDefault(OPTION_COPY_METHOD_NAME, builderMethodName);
        buildMethodName = options.getOrDefault(OPTION_BUILD_METHOD_NAME, "build");
        componentsMethodName = options.getOrDefault(OPTION_COMPONENTS_METHOD_NAME, "stream");
        fileComment = options.getOrDefault(OPTION_FILE_COMMENT,
                "Auto generated by io.soabase.recordbuilder.core.RecordBuilder: https://github.com/Randgalt/record-builder");
        fileIndent = options.getOrDefault(OPTION_FILE_INDENT, "    ");
        String prefixenclosingclassnamesopt = options.get(OPTION_PREFIX_ENCLOSING_CLASS_NAMES);
        if (prefixenclosingclassnamesopt == null) {
            prefixEnclosingClassNames = true;
        } else {
            prefixEnclosingClassNames = Boolean.parseBoolean(prefixenclosingclassnamesopt);
        }
    }

    @Override
    public String suffix() {
        return suffix;
    }

    @Override
    public String copyMethodName() {
        return copyMethodName;
    }

    @Override
    public String builderMethodName() {
        return builderMethodName;
    }

    @Override
    public String buildMethodName() {
        return buildMethodName;
    }

    @Override
    public String componentsMethodName() {
        return componentsMethodName;
    }

    @Override
    public String fileComment() {
        return fileComment;
    }

    @Override
    public String fileIndent() {
        return fileIndent;
    }

    @Override
    public boolean prefixEnclosingClassNames() {
        return prefixEnclosingClassNames;
    }

}
