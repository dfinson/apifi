package dev.sanda.apifi.generator.api_builder;

import dev.sanda.apifi.annotations.CRUDResolvers;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

@Data
public class EntityApiBuildInfo {
    private Map<String, CRUDResolvers> crudResolvers;
    private List<CustomResolverApiBuildInfo> customResolvers;

}
