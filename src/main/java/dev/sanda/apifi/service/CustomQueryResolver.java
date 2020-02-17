package dev.sanda.apifi.service;

import org.springframework.stereotype.Component;

@Component
public interface CustomQueryResolver<T, TReturn> extends CustomResolver<T, TReturn> {
}
