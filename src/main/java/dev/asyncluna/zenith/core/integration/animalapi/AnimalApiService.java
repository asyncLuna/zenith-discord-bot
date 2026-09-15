package dev.asyncluna.zenith.core.integration.animalapi;

import dev.asyncluna.zenith.core.util.HttpUtils;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnimalApiService {
    private final AnimalApiCache cache;

    public Mono<String> getRandomAnimalImageUrl(AnimalApiType animalType) {
        log.info("Fetching random animal image URL from Animality API for animalType={}", animalType);

        String cacheKey = "image:" + animalType.getApiName();
        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(AnimalApiEndpoint.BASE_URL)
                        .path(AnimalApiEndpoint.GET_RANDOM_IMAGE.getPath())
                        .buildAndExpand(animalType.getApiName())
                        .toUri();

        return getWithCache(AnimalApiEndpoint.GET_RANDOM_IMAGE, cacheKey, uriFunction, AnimalApiResponse.class)
                .map(AnimalApiResponse::image);
    }

    public Mono<String> getRandomAnimalFact(AnimalApiType animalType) {
        log.info("Fetching random animal fact from Animality API for animalType={}", animalType);

        String cacheKey = "fact:" + animalType.getApiName();
        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(AnimalApiEndpoint.BASE_URL)
                        .path(AnimalApiEndpoint.GET_RANDOM_FACT.getPath())
                        .buildAndExpand(animalType.getApiName())
                        .toUri();

        return getWithCache(AnimalApiEndpoint.GET_RANDOM_FACT, cacheKey, uriFunction, AnimalApiResponse.class)
                .map(AnimalApiResponse::fact);
    }

    public Mono<AnimalApiResponse> getRandomAnimalImageAndFact(AnimalApiType animalType) {
        log.info("Fetching random animal image and fact from Animality API for animalType={}", animalType);

        String cacheKey = "all:" + animalType.getApiName();
        Function<UriBuilder, URI> uriFunction =
                builder -> UriComponentsBuilder.fromUriString(AnimalApiEndpoint.BASE_URL)
                        .path(AnimalApiEndpoint.GET_IMAGE_AND_FACT.getPath())
                        .buildAndExpand(animalType.getApiName())
                        .toUri();

        return getWithCache(AnimalApiEndpoint.GET_IMAGE_AND_FACT, cacheKey, uriFunction, AnimalApiResponse.class);
    }

    private <T> Mono<T> getWithCache(
            AnimalApiEndpoint endpoint, String cacheKey, Function<UriBuilder, URI> uriFunction, Class<T> type) {
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
                    if (response != null) {
                        log.info("Cache updated for endpoint={} with cacheKey={}", endpoint, cacheKey);
                        return Mono.just(response);
                    } else {
                        log.warn("Failed to find cached item after population for key={}", cacheKey);
                        return Mono.empty();
                    }
                }));
    }

    private void logJsonResponse(AnimalApiEndpoint endpoint, String json) {
        log.info("Received JSON response for endpoint={}: {}", endpoint, json);
    }

    private <T> void cacheResponse(AnimalApiEndpoint endpoint, String cacheKey, T response) {
        cache.put(endpoint, cacheKey, response, endpoint.getTtlSeconds());
    }
}
