package com.jefiro.app247.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.config.ChannelRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Autowired TerminalHandshakeInterceptor terminalHandshakeInterceptor;
    @Autowired TerminalHandshakeHandler terminalHandshakeHandler;
    @Autowired TerminalStompAuthorizationInterceptor terminalStompAuthorizationInterceptor;
    @Autowired WebSocketOrigins webSocketOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(terminalHandshakeInterceptor)
                .setHandshakeHandler(terminalHandshakeHandler)
                .setAllowedOrigins(webSocketOrigins.values());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(terminalStompAuthorizationInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(terminalStompAuthorizationInterceptor);
    }
}
