package com.p2pchat.chat_service.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class WebSocketConfig {
    @Bean
    public HandlerMapping handlerMapping(ChatSocketHandler chatSocketHandler) {
        Map<String, WebSocketHandler> map = new HashMap<>();
        map.put("/chat", chatSocketHandler);

        return new SimpleUrlHandlerMapping(map, -1);
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}