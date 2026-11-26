package com.withwon.customer.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "withwon.customer.application")
public class CustomerApplicationProperties {

    private String schemaVersion;
    private List<ConsentForm> consentForms = new ArrayList<>();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<ConsentForm> getConsentForms() {
        return consentForms;
    }

    public void setConsentForms(List<ConsentForm> consentForms) {
        this.consentForms = consentForms;
    }

    public static class ConsentForm {
        private String consentType;
        private String consentVersion;
        private String content;

        public String getConsentType() {
            return consentType;
        }

        public void setConsentType(String consentType) {
            this.consentType = consentType;
        }

        public String getConsentVersion() {
            return consentVersion;
        }

        public void setConsentVersion(String consentVersion) {
            this.consentVersion = consentVersion;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
