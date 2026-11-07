package com.umis.mailbox.connector;

import com.umis.common.exception.BadRequestException;
import com.umis.mailbox.connector.model.ProviderCapability;
import com.umis.mailbox.model.MailboxProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry holding all available MailboxConnector provider implementations.
 */
@Component
public class MailboxConnectorRegistry {

    private final Map<MailboxProvider, MailboxConnector> connectorMap = new EnumMap<>(MailboxProvider.class);

    public MailboxConnectorRegistry(List<MailboxConnector> connectors) {
        for (MailboxConnector connector : connectors) {
            connectorMap.put(connector.getProvider(), connector);
        }
    }

    public MailboxConnector getConnector(MailboxProvider provider) {
        MailboxConnector connector = connectorMap.get(provider);
        if (connector == null) {
            throw new BadRequestException("Unsupported mailbox provider: " + provider);
        }
        return connector;
    }

    public List<ProviderCapability> getAllCapabilities() {
        return connectorMap.values().stream()
                .map(MailboxConnector::getCapability)
                .toList();
    }
}
