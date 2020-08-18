package dev.sanda.apifi.dto;

import dev.sanda.datafi.service.DataManager;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

import static com.google.common.collect.Maps.immutableEntry;

@Data
@AllArgsConstructor
public class KeyAndValue {
    private final Object key;
    private final Object value;
    public <TKey, TValue> Map.Entry<TKey, TValue> toEntry(){
        return (Map.Entry<TKey, TValue>) immutableEntry(key, value);
    }
}
