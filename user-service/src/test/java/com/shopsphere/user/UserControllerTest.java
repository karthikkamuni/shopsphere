package com.shopsphere.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"shopsphere.admin.name=Test Admin","shopsphere.admin.email=admin@test.example","shopsphere.admin.password=Administrator123","shopsphere.service-api-key=test-service-key-at-least-32-bytes-long"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class UserControllerTest {
    @Autowired MockMvc mvc;
    private String token(String email,String password) throws Exception {
        String json=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(json).get("accessToken").asText();
    }
    @Test void healthIsPublic() throws Exception { mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")); }
    @Test void internalCustomerDetailsRequireServiceKeyAndReturnMinimalFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Private Customer\",\"email\":\"private@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isCreated());
        String admin=token("admin@test.example","Administrator123");
        String users=mvc.perform(get("/api/users?q=private").header("Authorization","Bearer "+admin)).andReturn().getResponse().getContentAsString();
        long id=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(users).get(0).get("id").asLong();
        mvc.perform(get("/internal/users/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/"+id).header("X-Service-Token","test-service-key-at-least-32-bytes-long"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Private Customer"))
                .andExpect(jsonPath("$.email").value("private@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
    @Test void registrationLoginAndProfileWork() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Asha Rao\",\"email\":\"asha@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("USER"));
        String jwt=token("asha@example.com","password1234");
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+jwt)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("asha@example.com"));
        mvc.perform(put("/api/users/me").header("Authorization","Bearer "+jwt).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Asha R\",\"email\":\"asha@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
    }
    @Test void adminIsProvisionedAndCanListButUserCannot() throws Exception {
        String admin=token("admin@test.example","Administrator123");
        mvc.perform(get("/api/users").header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"User\",\"email\":\"ordinary@example.com\",\"password\":\"password1234\"}"));
        String user=token("ordinary@example.com","password1234");
        mvc.perform(get("/api/users").header("Authorization","Bearer "+user)).andExpect(status().isForbidden());
    }
    @Test void invalidLoginAndUnauthenticatedProfileAreRejected() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void duplicateRegistrationAndInvalidEmailAreRejected() throws Exception {
        String body="{\"name\":\"Asha\",\"email\":\"dup@example.com\",\"password\":\"password1234\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad\",\"email\":\"bad\",\"password\":\"password1234\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void blockingInvalidatesPreviouslyIssuedTokenImmediately() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\",\"email\":\"blocked@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isCreated());
        String admin=token("admin@test.example","Administrator123");
        String user=token("blocked@example.com","password1234");
        String body=mvc.perform(get("/api/users?q=blocked").header("Authorization","Bearer "+admin)).andReturn().getResponse().getContentAsString();
        long id=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(body).get(0).get("id").asLong();
        mvc.perform(patch("/api/users/"+id+"/status").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked").value(true));
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+user)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"blocked@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void passwordChangeRequiresCurrentPasswordAndInvalidatesOldCredential() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Password\",\"email\":\"password@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isCreated());
        String jwt=token("password@example.com","password1234");
        mvc.perform(put("/api/users/me/password").header("Authorization","Bearer "+jwt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password1234\",\"newPassword\":\"newpassword1234\"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"password@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"password@example.com\",\"password\":\"newpassword1234\"}"))
                .andExpect(status().isOk());
    }
}
