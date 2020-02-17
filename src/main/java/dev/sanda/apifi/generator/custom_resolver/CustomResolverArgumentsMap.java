package dev.sanda.apifi.generator.custom_resolver;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class CustomResolverArgumentsMap {
    @NonNull
    private Map<String, Object> argumentsMap;
    public <TArg> TArg get(String key){
        return argumentsMap.containsKey(key) ? (TArg)argumentsMap.get(key) : null;
    }
}
