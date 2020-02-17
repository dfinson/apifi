package dev.sanda.apifi.service;

import org.springframework.stereotype.Component;

@Component
public interface CustomMutationResolver<T, TReturn> extends CustomResolver<T, TReturn> {
}
