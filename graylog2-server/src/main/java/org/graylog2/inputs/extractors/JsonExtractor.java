/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.inputs.extractors;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auto.value.AutoValue;
import org.graylog.autovalue.WithBeanGetter;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import org.graylog2.ConfigurationException;
import org.graylog2.plugin.inputs.Converter;
import org.graylog2.plugin.inputs.Extractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;

public class JsonExtractor extends Extractor {
    private static final Logger LOG = LoggerFactory.getLogger(JsonExtractor.class);
    private static final String CK_FLATTEN = "flatten";
    private static final String CK_LIST_SEPARATOR = "list_separator";
    private static final String CK_KEY_SEPARATOR = "key_separator";
    private static final String CK_KV_SEPARATOR = "kv_separator";
    private static final String CK_REPLACE_KEY_WHITESPACE = "replace_key_whitespace";
    private static final String CK_KEY_WHITESPACE_REPLACEMENT = "key_whitespace_replacement";
    private static final String CK_KEY_PREFIX = "key_prefix";
    private static final RemoveNullPredicate REMOVE_NULL_PREDICATE = new RemoveNullPredicate();

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean flatten;
    private final String listSeparator;
    private final String keySeparator;
    private final String kvSeparator;
    private final boolean replaceKeyWhitespace;
    private final String keyWhitespaceReplacement;
    private final String keyPrefix;

    public JsonExtractor(final MetricRegistry metricRegistry,
                         final String id,
                         final String title,
                         final long order,
                         final CursorStrategy cursorStrategy,
                         final String sourceField,
                         final String targetField,
                         final Map<String, Object> extractorConfig,
                         final String creatorUserId,
                         final List<Converter> converters,
                         final ConditionType conditionType,
                         final String conditionValue) throws ReservedFieldException, ConfigurationException {
        super(metricRegistry, id, title, order, Type.JSON, cursorStrategy, sourceField, targetField, extractorConfig, creatorUserId, converters, conditionType, conditionValue);

        if (extractorConfig == null) {
            throw new ConfigurationException("Missing extractor configuration");
        }

        this.flatten = firstNonNull((Boolean) extractorConfig.get(CK_FLATTEN), false);
        this.listSeparator = firstNonNull((String) extractorConfig.get(CK_LIST_SEPARATOR), ", ");
        this.keySeparator = firstNonNull((String) extractorConfig.get(CK_KEY_SEPARATOR), "_");
        this.kvSeparator = firstNonNull((String) extractorConfig.get(CK_KV_SEPARATOR), "=");
        this.replaceKeyWhitespace = firstNonNull((Boolean) extractorConfig.get(CK_REPLACE_KEY_WHITESPACE), false);
        this.keyWhitespaceReplacement = firstNonNull((String) extractorConfig.get(CK_KEY_WHITESPACE_REPLACEMENT), "_");
        this.keyPrefix = firstNonNull((String) extractorConfig.get(CK_KEY_PREFIX), "");
    }

    @Override
    protected Result[] run(String value) {
        final Map<String, Object> extractedJson = extractJson(value);
        final List<Result> results = new ArrayList<>(extractedJson.size());
        for (Map.Entry<String, Object> entry : extractedJson.entrySet()) {
            results.add(new Result(entry.getValue(), entry.getKey(), -1, -1));
        }

        return results.toArray(new Result[results.size()]);
    }

    public Map<String, Object> extractJson(String value) {
        if (isNullOrEmpty(value)) {
            return Collections.emptyMap();
        }

        final Map<String, Object> json;
        try {
            json = mapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            return Collections.emptyMap();
        }

        final Map<String, Object> results = new HashMap<>(json.size());
        for (Map.Entry<String, Object> mapEntry : json.entrySet()) {
            String key = keyPrefix + mapEntry.getKey();
            if (replaceKeyWhitespace && key.contains(" ")) {
                key = key.replace(" ", keyWhitespaceReplacement);
            } else {
                if (LOG.isDebugEnabled()) {
                    if (key.contains(" ")) {
                        LOG.debug("Invalid key \"{}\" in JSON object!", key);
                    }
                }
            }
            for (Entry entry : parseValue(key, mapEntry.getValue())) {
                results.put(entry.key(), entry.value());
            }
        }

        return results;
    }

    private Collection<Entry> parseValue(String key, Object value) {
        if (value instanceof Boolean) {
            return Collections.singleton(Entry.create(key, value));
        } else if (value instanceof Number) {
            return Collections.singleton(Entry.create(key, value));
        } else if (value instanceof String) {
            return Collections.singleton(Entry.create(key, value));
        } else if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = (Map<String, Object>) value;
            final Map<String, Object> withoutNull = Maps.filterEntries(map, REMOVE_NULL_PREDICATE);
            if (flatten) {
                final Joiner.MapJoiner joiner = Joiner.on(listSeparator).withKeyValueSeparator(kvSeparator);
                return Collections.singleton(Entry.create(key, joiner.join(withoutNull)));
            } else {
                final List<Entry> result = new ArrayList<>(map.size());
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    result.addAll(parseValue(key + keySeparator + entry.getKey(), entry.getValue()));
                }

                return result;
            }
        } else if (value instanceof List) {
            final List values = (List) value;
            final Joiner joiner = Joiner.on(listSeparator).skipNulls();
            return Collections.singleton(Entry.create(key, joiner.join(values)));
        } else if (value == null) {
            // Ignore null values so we don't try to create fields for that in the message.
            return Collections.emptySet();
        } else {
            LOG.debug("Unknown type \"{}\" in key \"{}\"", value.getClass(), key);
            return Collections.emptySet();
        }
    }

    @AutoValue
@WithBeanGetter
    protected abstract static class Entry {
        public abstract String key();

        @Nullable
        public abstract Object value();

        public static Entry create(String key, @Nullable Object value) {
            return new AutoValue_JsonExtractor_Entry(key, value);
        }
    }

    protected final static class RemoveNullPredicate implements Predicate<Map.Entry> {
        @Override
        public boolean apply(@Nullable Map.Entry input) {
            return input != null && input.getKey() != null && input.getValue() != null;
        }
    }
}
