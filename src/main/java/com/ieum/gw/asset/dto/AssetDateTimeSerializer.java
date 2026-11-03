package com.ieum.gw.asset.dto;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/** DB의 서울 시각을 초 단위 고정 형식으로 보낸다. 시간대를 변환하거나 Z를 붙이지 않는다. */
public class AssetDateTimeSerializer extends JsonSerializer<LocalDateTime> {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");

    @Override
    public void serialize(LocalDateTime value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(value.format(FORMAT));
    }
}
