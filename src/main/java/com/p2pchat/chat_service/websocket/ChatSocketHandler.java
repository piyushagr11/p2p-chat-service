package com.p2pchat.chat_service.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.p2pchat.chat_service.dto.ChatMessage;
import com.p2pchat.chat_service.entity.Message;
import com.p2pchat.chat_service.security.JwtUtil;
import com.p2pchat.chat_service.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSocketHandler implements WebSocketHandler {

    private final JwtUtil jwtUtil;
    private final MessageRepository messageRepository;
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                    new JsonPrimitive(src.toString()))
            .create();

    private static final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query == null || !query.startsWith("token=")) {
            log.error("Missing or malformed token");
            return session.close(CloseStatus.BAD_DATA.withReason("Token required"));
        }

        String token = query.replace("token=", "");

        if (!jwtUtil.validateToken(token)) {
            log.error("Invalid token: {}", token);
            return session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid token"));
        }

        String sender = jwtUtil.extractUsername(token);
        log.info("Connected user: {}", sender);
        activeSessions.put(sender, session);

        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(msg -> {
                    try {
                        return gson.fromJson(msg, ChatMessage.class);
                    } catch (Exception e) {
                        log.error("Failed to parse JSON: {}", msg, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(chatMsg -> {
                    log.info("Handling chatMsg: sender={}, receiver={}, content={}", sender, chatMsg.receiver(), chatMsg.content());
                    Message message = Message.builder()
                            .sender(sender)
                            .receiver(chatMsg.receiver())
                            .content(chatMsg.content())
                            .timestamp(Instant.now())
                            .build();

                    Mono<Message> saved = messageRepository.save(message)
                            .doOnSubscribe(sub -> log.info("Saving message to DB..."))
                            .doOnSuccess(m -> log.info("Saved to DB for {} -> {}", m.getSender(), m.getReceiver()))
                            .doOnError(e -> log.error("Error while saving to DB", e));

                    WebSocketSession receiverSession = activeSessions.get(chatMsg.receiver());

                    if (receiverSession != null && receiverSession.isOpen()) {
                        log.info("Delivering to online user: {}", chatMsg.receiver());
                        return saved.flatMap(m -> {
                            String json = gson.toJson(m);
                            return receiverSession.send(Mono.just(receiverSession.textMessage(json)))
                                    .doOnSubscribe(sub -> log.info("Sending to {}...", chatMsg.receiver()))
                                    .doOnSuccess(unused -> log.info("Sent message to {}", chatMsg.receiver()))
                                    .doOnError(e -> log.error("Send to {} failed: {}", chatMsg.receiver(), e.getMessage(), e))
                                    .onErrorResume(e -> {
                                        log.warn("Fallback for {}: {}", chatMsg.receiver(), e.getMessage());
                                        return Mono.empty();
                                    })
                                    .then();
                        });
                    }

                    log.info("User '{}' is offline, message saved only.", chatMsg.receiver());
                    return saved.then();
                })
                .doFinally(signal -> {
                    log.info("Disconnected user: {}", sender);
                    activeSessions.remove(sender);
                })
                .then()
                .onErrorResume(e -> {
                    log.error("Fatal error in WebSocket stream: {}", e.getMessage(), e);
                    return session.close(CloseStatus.SERVER_ERROR);
                });
    }
}
