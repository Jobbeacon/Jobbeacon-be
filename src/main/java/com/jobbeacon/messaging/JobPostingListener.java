package com.jobbeacon.messaging;

import com.jobbeacon.dto.message.JobPostingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobPostingListener {

    private final com.jobbeacon.service.JobService jobService;

    @RabbitListener(queues = "${app.rabbitmq.job-posting.queue}")
    public void handleJobPostingMessage(JobPostingMessage message) {
        log.debug("Received job posting: {}", message);
        try {
            jobService.processJobPostingMessage(message);
        } catch (Exception e) {
            log.error("Error processing job posting message: ", e);
        }
    }
}
