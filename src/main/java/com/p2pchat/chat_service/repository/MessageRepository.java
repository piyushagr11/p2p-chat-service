package com.p2pchat.chat_service.repository;

import com.p2pchat.chat_service.entity.Message;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface MessageRepository extends ReactiveMongoRepository<Message, String> {
    Flux<Message> findBySenderAndReceiverOrReceiverAndSender(String sender1, String receiver1, String sender2, String receiver2);
}