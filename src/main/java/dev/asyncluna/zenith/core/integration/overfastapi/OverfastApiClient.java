package dev.asyncluna.zenith.core.integration.overfastapi;

import dev.asyncluna.zenith.core.util.HttpUtils;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class OverfastApiClient {
    private final OverfastApiCache cache;

    public <T> Mono<T> get(
            OverfastApiEndpoint endpoint, String cacheKey, Function<UriBuilder, URI> uriFunction, Class<T> type) {
        T cached = cache.get(endpoint, cacheKey, type);
        if (cached != null) {
            log.info("Cache hit for endpoint={} with cacheKey={}", endpoint, cacheKey);
            return Mono.just(cached);
        }

        return HttpUtils.WEB_CLIENT
                .get()
                .uri(uriBuilder -> {
                    URI finalUri = uriFunction.apply(uriBuilder);
                    log.info("Outbound WebClient executing URI request layout: {}", finalUri);
                    return finalUri;
                })
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(json -> logJsonResponse(endpoint, json))
                .map(json -> HttpUtils.OBJECT_MAPPER.readValue(json, type))
                .onErrorMap(
                        IOException.class,
                        exception -> new RuntimeException("Failed to parse JSON response", exception))
                .doOnNext(response -> cacheResponse(endpoint, cacheKey, response))
                .then(Mono.defer(() -> {
                    T response = cache.get(endpoint, cacheKey, type);
                    if (response != null) return Mono.just(response);
                    log.warn("Failed to find cached item after population for key={}", cacheKey);
                    return Mono.empty();
                }));
    }

    private void logJsonResponse(OverfastApiEndpoint endpoint, String json) {
        log.info("Received JSON response for endpoint={}: {}", endpoint, json);
    }

    private <T> void cacheResponse(OverfastApiEndpoint endpoint, String cacheKey, T response) {
        cache.put(endpoint, cacheKey, response, endpoint.getTtlSeconds());
    }
}
