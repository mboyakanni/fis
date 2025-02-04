package it.niedermann.fis.operation.remote.notification.sms;

import it.niedermann.fis.main.model.OperationDto;
import it.niedermann.fis.operation.remote.notification.NotificationConfiguration;
import it.niedermann.fis.operation.remote.notification.OperationNotificationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmsProviderTest {

    private SmsProvider provider;
    private NotificationConfiguration config;

    @BeforeEach()
    public void setup() {
        config = mock(NotificationConfiguration.class);
    }

    @Test
    public void shouldHandleNotConfiguredRecipients() {
        when(config.sms()).thenReturn(null);
        provider = new SmsProvider(config, mock(OperationNotificationUtil.class)) {
            @Override
            public void accept(OperationDto operationDto) {
                assertEquals(0, recipients.size());
            }
        };
        provider.accept(mock(OperationDto.class));
    }

    @Test
    public void shouldFilterInvalidPhoneNumbers() {
        when(config.sms()).thenReturn(List.of("2055550125", "foobar", "123"));
        provider = new SmsProvider(config, mock(OperationNotificationUtil.class)) {
            @Override
            public void accept(OperationDto operationDto) {
                assertEquals(1, recipients.size());
            }
        };
        provider.accept(mock(OperationDto.class));
    }
}
