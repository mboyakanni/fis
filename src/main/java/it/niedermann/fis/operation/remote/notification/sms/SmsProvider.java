package it.niedermann.fis.operation.remote.notification.sms;

import it.niedermann.fis.main.model.OperationDto;
import it.niedermann.fis.operation.remote.notification.NotificationConfiguration;
import it.niedermann.fis.operation.remote.notification.OperationNotificationUtil;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public abstract class SmsProvider implements Consumer<OperationDto> {

    protected final Optional<String> apiKey;
    protected final Collection<String> recipients;
    protected final String senderName;
    protected final OperationNotificationUtil notificationUtil;
    protected final boolean priority;

    public SmsProvider(
            NotificationConfiguration config,
            OperationNotificationUtil notificationUtil) {
        this.notificationUtil = notificationUtil;
        this.apiKey = Optional.ofNullable(config.smsApiKey());
        this.senderName = config.senderName();
        this.recipients = filterPhoneRecipients(config.sms());
        this.priority = config.smsPriority();
    }

    protected String getMessage(OperationDto operation) {
        final var address = notificationUtil.getHumanReadableLocation(operation);
        return address.isPresent()
                ? String.format("Einsatz: %s, %s",
                        operation.getKeyword(),
                        String.join(", ", operation.getTags()))
                : String.format("Einsatz: %s, Karte: %s, Adresse: %s",
                        operation.getKeyword(),
                        notificationUtil.getGoogleMapsLink(operation),
                        address);
    }

    private Collection<String> filterPhoneRecipients(Collection<String> recipients) {
        return recipients == null
                ? Collections.emptyList()
                : recipients
                        .stream()
                        .filter(this::isValidPhoneNumber)
                        .map(this::sanitizePhoneNumber)
                        .toList();
    }

    protected boolean isValidPhoneNumber(String source) {
        return source != null && sanitizePhoneNumber(source).length() > 6;
    }

    protected String sanitizePhoneNumber(String source) {
        return source.replaceAll("\\D", "");
    }
}
