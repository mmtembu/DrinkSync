package com.smarteventbar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${websocket.endpoint:/ws}")
    private String endpoint;

    @Value("${websocket.application-destination-prefix:/app}")
    private String applicationDestinationPrefix;

    @Value("${websocket.broker-prefix:/topic}")
    private String brokerPrefix;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker(brokerPrefix);
        config.setApplicationDestinationPrefixes(applicationDestinationPrefix);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
