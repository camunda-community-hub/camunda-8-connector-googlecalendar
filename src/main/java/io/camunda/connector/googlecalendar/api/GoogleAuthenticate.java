package io.camunda.connector.googlecalendar.api;

/**
 * Holds OAuth2 credentials required to authenticate against the Google Calendar API.
 * Obtain clientId, clientSecret and refreshToken from the Google Cloud Console
 * (APIs & Services → Credentials → OAuth 2.0 Client IDs).
 */
public class GoogleAuthenticate {

    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String applicationName;

    public GoogleAuthenticate() {
    }

    public GoogleAuthenticate(String clientId, String clientSecret, String refreshToken, String applicationName) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.applicationName = applicationName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName != null ? applicationName : "Camunda Google Calendar Connector";
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
