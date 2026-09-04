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

    @RabbitListener(queues = "${app.rabbitmq.job-posting.queue}")
    public void handleJobPostingMessage(JobPostingMessage message) {
        log.info("Received job posting: {}", message);
        // TODO: Save the job posting to database
    }
}
