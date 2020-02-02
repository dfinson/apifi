package dev.sanda.apifi.generator.api_builder;

import com.squareup.javapoet.ClassName;
import lombok.Data;

@Data
public class ArgumentParameter {
    private String name;
    private String rawName;
    private ClassName type;
}
