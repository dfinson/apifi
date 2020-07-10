package dev.sanda.apifi.generator.entity;

import com.squareup.javapoet.TypeSpec;
import lombok.AllArgsConstructor;
import lombok.Data;

import javax.lang.model.element.TypeElement;

@Data
@AllArgsConstructor
public class ServiceTestableServiceAndTest {
    private TypeSpec service;
    private TypeSpec testableService;
    private TypeSpec test;
}
