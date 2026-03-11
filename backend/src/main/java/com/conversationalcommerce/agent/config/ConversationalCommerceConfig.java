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

    public String projectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId != null ? projectId : ""; }

    public String placement() { return placement; }
    public void setPlacement(String placement) { this.placement = placement != null ? placement : ""; }

    public String branch() { return branch; }
    public void setBranch(String branch) { this.branch = branch != null ? branch : ""; }

    public String defaultVisitorId() { return defaultVisitorId; }
    public void setDefaultVisitorId(String defaultVisitorId) { this.defaultVisitorId = defaultVisitorId != null ? defaultVisitorId : "default-visitor"; }
}
