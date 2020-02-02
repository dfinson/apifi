package dev.sanda.apifi.generator.api_builder;

import org.springframework.stereotype.Component;

@Component
public interface CustomMutationResolver<T, TReturn> extends CustomResolver<T, TReturn> {
}
