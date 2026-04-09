package com.finflow.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.account.controller.AccountController;
import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.exception.AccountNotFoundException;
import com.finflow.account.exception.GlobalExceptionHandler;
import com.finflow.account.service.AccountService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockBean private AccountService accountService;

    @Test
    void createAccount_Returns201_WhenValid() throws Exception {
        AccountRequestDto body =
                new AccountRequestDto(
                        "new@finflow.com", "Jane", "Doe", "+19998887777", "tenant-1", null);
        AccountResponseDto response =
                new AccountResponseDto(
                        "new-account-id",
                        body.email(),
                        body.firstName(),
                        body.lastName(),
                        body.phoneNumber(),
                        "ACTIVE",
                        body.tenantId(),
                        true,
                        LocalDateTime.now(),
                        LocalDateTime.now());

        when(accountService.createAccount(any(AccountRequestDto.class), any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("new-account-id"));
    }

    @Test
    void createAccount_Returns400_WhenEmailMissing() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "firstName", "John",
                        "lastName", "Doe",
                        "phoneNumber", "+1234567890");

        mockMvc.perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void getAccount_Returns404_WhenNotFound() throws Exception {
        when(accountService.getAccountById("non-existent-id"))
                .thenThrow(new AccountNotFoundException("non-existent-id"));

        mockMvc.perform(get("/api/accounts/non-existent-id"))
                .andExpect(status().isNotFound());
    }
}
