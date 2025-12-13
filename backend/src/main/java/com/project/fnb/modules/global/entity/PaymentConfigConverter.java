package com.project.fnb.modules.global.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.fnb.common.utils.CryptoUtils;
import com.project.fnb.modules.global.dto.PaymentConfigDto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentConfigConverter implements AttributeConverter<PaymentConfigDto, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(PaymentConfigDto attribute) {
        if (attribute == null) return null;
        try {
            String json = objectMapper.writeValueAsString(attribute);
            return CryptoUtils.encrypt(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON write error", e);
        }
    }

    @Override
    public PaymentConfigDto convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        try {
            String json = CryptoUtils.decrypt(dbData);
            return objectMapper.readValue(json, PaymentConfigDto.class);
        } catch (Exception e) {
            throw new RuntimeException("JSON read error", e);
        }
    }
}