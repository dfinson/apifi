package dev.sanda.apifi.generator.custom_resolver;

import com.squareup.javapoet.ClassName;
import lombok.Data;

@Data
public class CustomResolverArgumentParameter {
    private String name;
    private ClassName type;
    private String nameWithType;
}
