package dev.sanda.apifi.generator.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@AllArgsConstructor
public class CollectionsTypeResolver {
    private final Map<String, Class> collectionsTypes;
    public Class resolveFor(String key){
        return collectionsTypes.get(key);
    }
}
