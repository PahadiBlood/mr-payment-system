package xyz.rawmanoj.mrbank.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationContext;

import java.nio.charset.StandardCharsets;

public class CustomRedisSerializer implements RedisSerializer<Object> {

    private final ObjectMapper objectMapper;

    public CustomRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object source) {
        if (source == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(source);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object", e);
        }
    }

    @Override
    public Object deserialize(byte[] source) {
        if (source == null || source.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(source, Object.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize object", e);
        }
    }

    @Override
    public Object deserialize(byte[] source, Class<?> type) {
        if (source == null || source.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(source, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize object to type: " + type.getName(), e);
        }
    }
}
