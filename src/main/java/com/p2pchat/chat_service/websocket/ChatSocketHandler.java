package com.p2pchat.chat_service.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.p2pchat.chat_service.dto.GenericMessage;
import com.p2pchat.chat_service.dto.TypingSignal;
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
                        return gson.fromJson(msg, GenericMessage.class);
                    } catch (Exception e) {
                        log.error("Failed to parse JSON: {}", msg, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(payload -> {
                    switch (payload.type()){
                        case "MESSAGE":
                            Message chatMsg = Message.builder()
                                    .sender(sender)
                                    .receiver(payload.receiver())
                                    .content(payload.content())
                                    .timestamp(Instant.now())
                                    .build();
                            Mono<Message> saved = messageRepository.save(chatMsg);

                            return sendToReceiver(payload.receiver(), saved);
                        case "TYPING":
                            return sendTypingSignal(sender, payload.receiver());
                        default:
                            log.warn("Unknown message type: {}", payload.type());
                            return Mono.empty();
                    }
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

    private Mono<Void> sendToReceiver(String receiver, Mono<Message> messageMono) {
        WebSocketSession receiverSession = activeSessions.get(receiver);

        return messageMono.flatMap(message -> {
            String json = gson.toJson(message);
            if (receiverSession != null && receiverSession.isOpen()) {
                return receiverSession.send(Mono.just(receiverSession.textMessage(json))).then();
            }
            return Mono.empty();
        });
    }

    private Mono<Void> sendTypingSignal(String sender, String receiver) {
        WebSocketSession receiverSession = activeSessions.get(receiver);

        if (receiverSession != null && receiverSession.isOpen()) {
            TypingSignal signal = new TypingSignal(sender, true);
            String json = gson.toJson(signal);
            return receiverSession.send(Mono.just(receiverSession.textMessage(json))).then();
        }
        return Mono.empty();
    }
}
