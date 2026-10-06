package com.jobbeacon.configuration;

import com.jobbeacon.service.base.MessageService;
import com.jobbeacon.service.base.MessageServiceImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GeocodeProperties.class)
public class ServiceConfiguration {

  @Bean
  public MessageService messageService(MessageSource messageSource) {
    return new MessageServiceImpl(messageSource);
  }
}
