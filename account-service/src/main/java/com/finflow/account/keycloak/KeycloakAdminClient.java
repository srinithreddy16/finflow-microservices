package com.finflow.account.keycloak;

import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.exception.DuplicateAccountException;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

//KeycloakAdminClient.java
//Uses that connection to actually create/delete/update users in Keycloak whenever someone signs up, gets suspended, or their account is deleted.

@Slf4j
@Service
public class KeycloakAdminClient {

    private final Keycloak keycloakAdmin;
    private final String realm;

    public KeycloakAdminClient(
            Keycloak keycloakAdmin,
            @Value("${keycloak.realm}") String realm) {
        this.keycloakAdmin = keycloakAdmin;
        this.realm = realm;
    }

    public String createUser(AccountRequestDto request) {
        try {
            UserRepresentation user = new UserRepresentation();
            user.setUsername(request.email());
            user.setEmail(request.email());
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setEnabled(true);
            user.setEmailVerified(true);

            CredentialRepresentation password = new CredentialRepresentation();
            password.setType(CredentialRepresentation.PASSWORD);
            password.setTemporary(true);
            // Temporary bootstrap password; user must change after first login flow.
            password.setValue("Temp@" + java.util.UUID.randomUUID().toString().substring(0, 8));
            user.setCredentials(List.of(password));

            Response response = keycloakAdmin.realm(realm).users().create(user);
            int status = response.getStatus();
            if (status == 201) {
                String userId = extractUserId(response);
                log.info("Created Keycloak user for email: {}", request.email());
                return userId;
            }
            if (status == 409) {
                throw new DuplicateAccountException(request.email());
            }
            throw FinFlowException.internalError(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to create Keycloak user, status: " + status,
                    null);
        } catch (DuplicateAccountException ex) {
            throw ex;
        } catch (Exception ex) {
            throw FinFlowException.internalError(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to create Keycloak user for email: " + request.email(),
                    ex);
        }
    }

    public void deleteUser(String keycloakUserId) {
        try {
            keycloakAdmin.realm(realm).users().get(keycloakUserId).remove();
            log.info("Deleted Keycloak user: {}", keycloakUserId);
        } catch (Exception ex) {
            log.warn("Failed to delete Keycloak user during compensation: {}", keycloakUserId, ex);
        }
    }

    public void assignRole(String keycloakUserId, String roleName) {
        RoleRepresentation role =
                keycloakAdmin.realm(realm).roles().get(roleName).toRepresentation();
        keycloakAdmin
                .realm(realm)
                .users()
                .get(keycloakUserId)
                .roles()
                .realmLevel()
                .add(List.of(role));
        log.info("Assigned role {} to user {}", roleName, keycloakUserId);
    }

    public void updateUserStatus(String keycloakUserId, boolean enabled) {
        UserRepresentation userRepresentation =
                keycloakAdmin.realm(realm).users().get(keycloakUserId).toRepresentation();
        userRepresentation.setEnabled(enabled);
        keycloakAdmin.realm(realm).users().get(keycloakUserId).update(userRepresentation);
    }

    private static String extractUserId(Response response) {
        URI location = response.getLocation();
        if (location == null) {
            throw FinFlowException.internalError(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    "Keycloak returned 201 without Location header",
                    null);
        }
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
