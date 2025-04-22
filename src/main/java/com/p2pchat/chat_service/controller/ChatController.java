package com.p2pchat.chat_service.controller;

import com.p2pchat.chat_service.entity.Message;
import com.p2pchat.chat_service.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
public class ChatController {

    private final MessageRepository messageRepository;

    @GetMapping("/{withUser}")
    public Flux<Message> getChat(@PathVariable String withUser, Authentication auth) {
        String me = auth.getName();
        return messageRepository.findBySenderAndReceiverOrReceiverAndSender(me, withUser, me, withUser);
    }
}
