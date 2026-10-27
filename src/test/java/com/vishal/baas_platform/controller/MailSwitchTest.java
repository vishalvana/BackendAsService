package com.vishal.baas_platform.controller;

import com.vishal.baas_platform.service.EmailService;
import com.vishal.baas_platform.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MailSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(EmailService.class, () -> mock(EmailService.class))
            .withBean(OtpService.class, () -> mock(OtpService.class))
            .withUserConfiguration(EmailController.class, OtpController.class);

    @Test
    void mailRoutesAreOnByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(EmailController.class);
            assertThat(context).hasSingleBean(OtpController.class);
        });
    }

    @Test
    void mailRoutesAreGoneWhenSwitchedOff() {
        runner.withPropertyValues("baas.mail.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(EmailController.class);
            assertThat(context).doesNotHaveBean(OtpController.class);
        });
    }
}
