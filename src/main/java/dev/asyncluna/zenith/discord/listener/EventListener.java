package dev.asyncluna.zenith.discord.listener;

import discord4j.core.event.domain.Event;
import io.sentry.Sentry;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public interface EventListener<T extends Event> {
    Logger LOG = LoggerFactory.getLogger(EventListener.class);

    Mono<Void> execute(T event);

    default Mono<Void> executeAsync(T event) {
        return execute(event);
    }

    @SuppressWarnings("unchecked")
    default Class<T> getEventType() {
        try {
            for (Type type : getClass().getGenericInterfaces()) {
                if (type instanceof ParameterizedType parameterizedType
                        && parameterizedType.getRawType().equals(EventListener.class)) {
                    return (Class<T>) parameterizedType.getActualTypeArguments()[0];
                }
            }
        } catch (RuntimeException exception) {
            LOG.error("Failed to resolve event type for listener {}", getClass().getName(), exception);
        }
        throw new IllegalStateException(
                "Could not resolve generic event type for " + getClass().getSimpleName());
    }

    default Mono<Void> handleException(Throwable exception) {
        LOG.error("Unhandled error processing event {}", getEventType().getSimpleName(), exception);
        Sentry.captureException(exception);
        return Mono.empty();
    }
}
