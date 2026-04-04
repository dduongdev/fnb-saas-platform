package com.project.fnb.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.TimeZone;

@Configuration
public class JacksonDateTimeConfig {

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer utcTimestampCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone("UTC"));
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeToEpochMillisSerializer());
            builder.deserializerByType(LocalDateTime.class, new EpochMillisToLocalDateTimeDeserializer());
        };
    }

    static class LocalDateTimeToEpochMillisSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
                return;
            }
            gen.writeNumber(value.toInstant(UTC).toEpochMilli());
        }
    }

    static class EpochMillisToLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonToken token = parser.currentToken();

            if (token == JsonToken.VALUE_NUMBER_INT) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(parser.getLongValue()), UTC);
            }

            if (token == JsonToken.VALUE_STRING) {
                String value = parser.getValueAsString();
                if (value == null || value.isBlank()) {
                    return null;
                }

                try {
                    long epochMillis = Long.parseLong(value);
                    return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), UTC);
                } catch (NumberFormatException ignored) {
                    // Fallback for ISO date-time payloads to keep backward compatibility.
                }

                try {
                    return LocalDateTime.ofInstant(Instant.parse(value), UTC);
                } catch (Exception ignored) {
                    return LocalDateTime.parse(value);
                }
            }

            return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
        }
    }
}
