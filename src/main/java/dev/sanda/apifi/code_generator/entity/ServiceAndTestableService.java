package dev.sanda.apifi.code_generator.entity;

import com.squareup.javapoet.TypeSpec;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ServiceAndTestableService {
    private TypeSpec service;
    private TypeSpec testableService;
}
