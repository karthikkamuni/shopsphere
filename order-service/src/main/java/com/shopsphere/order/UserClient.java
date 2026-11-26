package com.shopsphere.order;
import org.springframework.beans.factory.annotation.Value;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;import org.springframework.web.client.HttpClientErrorException;import org.springframework.web.client.RestClient;import org.springframework.web.client.RestClientException;import org.springframework.web.server.ResponseStatusException;
@Component public class UserClient {
 private final RestClient client;private final String token;
 public UserClient(RestClient.Builder builder,@Value("${services.user.url}")String url,@Value("${shopsphere.service-api-key:}")String token){this.client=builder.baseUrl(url).build();this.token=token;}
 public boolean active(Long id){try{AccountStatus r=client.get().uri("/internal/users/{id}/exists",id).header("X-Service-Token",token).retrieve().body(AccountStatus.class);return r!=null&&r.exists()&&r.active();}
  catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"user-service account validation failed");}}
 public CustomerDetails details(Long id){try{return client.get().uri("/internal/users/{id}",id).header("X-Service-Token",token).retrieve().body(CustomerDetails.class);}
  catch(HttpClientErrorException.NotFound e){return null;}
  catch(RestClientException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"user-service customer lookup failed");}}
}
record AccountStatus(boolean exists,boolean active){}
record CustomerDetails(Long id,String name,String email,boolean active){}
