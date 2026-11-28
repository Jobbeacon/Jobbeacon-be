package com.jobbeacon.configuration;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfiguration {

    @Value("${app.rabbitmq.job-posting.queue}")
    private String jobPostingQueueName;

    @Value("${app.rabbitmq.events.exchange}")
    private String eventsExchangeName;

    @Bean
    public Queue jobPostingQueue() {
        return new Queue(jobPostingQueueName, true);
    }

    /** Shared exchange for integration events (user.upserted, ...); consumers bind their own queues to it. */
    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(eventsExchangeName, true, false);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
