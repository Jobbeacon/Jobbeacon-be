package com.jobbeacon.configuration;

import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.DEFAULT_LANGUAGE;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.ENCODING_UTF_8;
import static com.jobbeacon.constant.JobBeaconConstants.CommonConstants.MESSAGE_SOURCE;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class MessageSourceConfiguration {

  @Bean
  public MessageSource messageSource() {
    var messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setBasenames(MESSAGE_SOURCE);
    messageSource.setDefaultEncoding(ENCODING_UTF_8);
    return messageSource;
  }

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(new Locale(DEFAULT_LANGUAGE));
    resolver.setSupportedLocales(List.of(new Locale("vi"), Locale.ENGLISH));
    return resolver;
  }
}
