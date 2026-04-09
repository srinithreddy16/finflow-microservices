package com.finflow.account.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Bean
    public Keycloak keycloak() {
        // Admin client authenticates against master realm using client credentials.
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)    // Where is Keycloak running?
                .realm("master")         // Which admin realm?
                .clientId(clientId)      // Who are we? (our app's identity
                .clientSecret(clientSecret)  // Our app's password
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)  // Machine to machine login
                .build();
    }
}


//KeycloakAdminConfig.java
//Sets up your backend app's connection to Keycloak's Admin API — like a DataSource bean but for Keycloak instead of PostgreSQL.

/*
Step 1:
Customer fills in signup form
POST /api/accounts
{ email: "john@gmail.com", password: "secret123" }

Step 2:
AccountService saves John to account_db (our PostgreSQL)
account_db now has John's name, email, tenant etc.
But John CANNOT log in yet — he has no credentials in Keycloak

Step 3:
AccountService calls KeycloakAdminClient.createUser()
KeycloakAdminClient uses the Keycloak bean (from KeycloakAdminConfig)
to call Keycloak Admin API:
        "Hey Keycloak, create a user john@gmail.com with password secret123"

Step 4:
Keycloak creates John in its own database
Keycloak returns John's Keycloak user ID: "kc-user-abc-123"

Step 5:
AccountService stores that Keycloak user ID in our account_db:
account_db: keycloak_user_id = "kc-user-abc-123"

Now John exists in TWO places:
        - account_db     → has his business data (tenantId, KYC status etc.)
- Keycloak       → has his login credentials (email + password)

Step 6:
John can now log in:
POST /auth/login { email, password }
Keycloak checks → valid → returns JWT token ✅
*/
