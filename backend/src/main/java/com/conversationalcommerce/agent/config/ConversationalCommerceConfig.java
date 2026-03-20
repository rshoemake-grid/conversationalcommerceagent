package com.conversationalcommerce.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "conversational-commerce")
public class ConversationalCommerceConfig {

    private String projectId = "";
    private String placement = "";
    private String branch = "";
    private String defaultVisitorId = "default-visitor";
    /** "rest" (default) or "grpc" - rest bypasses ALPN/VPN issues */
    private String transport = "rest";
    /** "DISABLED" (default), "ENABLED", or "CONVERSATIONAL_FILTER_ONLY". ENABLED lets the agent ask follow-up questions. */
    private String conversationalFilteringMode = "DISABLED";

    public String projectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId != null ? projectId : ""; }

    public String placement() { return placement; }
    public void setPlacement(String placement) { this.placement = placement != null ? placement : ""; }

    public String branch() { return branch; }
    public void setBranch(String branch) { this.branch = branch != null ? branch : ""; }

    public String defaultVisitorId() { return defaultVisitorId; }
    public void setDefaultVisitorId(String defaultVisitorId) { this.defaultVisitorId = defaultVisitorId != null ? defaultVisitorId : "default-visitor"; }

    public String transport() { return transport != null ? transport : "rest"; }
    public void setTransport(String transport) { this.transport = transport != null ? transport : "rest"; }

    public String conversationalFilteringMode() {
        return conversationalFilteringMode != null ? conversationalFilteringMode : "DISABLED";
    }
    public void setConversationalFilteringMode(String mode) {
        this.conversationalFilteringMode = mode != null ? mode : "DISABLED";
    }
}
