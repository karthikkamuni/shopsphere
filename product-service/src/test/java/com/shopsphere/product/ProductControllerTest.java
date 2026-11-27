package com.shopsphere.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ProductControllerTest {
 @Autowired MockMvc mvc; @Autowired ProductRepository repo; @Autowired ProductService service; @Autowired CatalogSeeder seeder;
 private String jwt(String role)throws Exception{
  var enc=Base64.getUrlEncoder().withoutPadding();String h=enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
  String p=enc.encodeToString(("{\"sub\":1,\"role\":\""+role+"\",\"iat\":1760000000,\"exp\":1990000000}").getBytes(StandardCharsets.UTF_8));String data=h+"."+p;
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec("test-secret-that-is-definitely-longer-than-32-bytes".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return data+"."+enc.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
 }
 private String admin()throws Exception{return "Bearer "+jwt("ADMIN");}
 @Test void healthAndReadinessArePublic()throws Exception{mvc.perform(get("/actuator/health")).andExpect(status().isOk());mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());}
 @Test void catalogSeedsFifteenProductsAndBrowsePreservesLegacyArrayContract()throws Exception{
  org.assertj.core.api.Assertions.assertThat(repo.findAll()).hasSizeGreaterThanOrEqualTo(15);
  mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name=='Apple iPhone 15')]").exists());
  mvc.perform(get("/api/products").param("category","Audio").param("sort","price,asc")).andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("Audio"));
  mvc.perform(get("/api/products/page").param("page","0").param("size","5")).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(5));
 }
 @Test void seedIsIdempotentAndAdminChangesSurviveRepeatedInitializer(){long before=repo.count();Product seeded=repo.findAll().stream().filter(p->p.getName().equals("Apple iPhone 15")).findFirst().orElseThrow();
  seeded.setStock(777);repo.saveAndFlush(seeded);seeder.seed();org.assertj.core.api.Assertions.assertThat(repo.count()).isEqualTo(before);
  org.assertj.core.api.Assertions.assertThat(repo.findById(seeded.getId()).orElseThrow().getStock()).isEqualTo(777);}
 @Test void adminCanCreateUpdateDeactivateButAnonymousCannotMutate()throws Exception{
  String body="{\"name\":\"Test Product A\",\"description\":\"Test\",\"price\":15.00,\"category\":\"Test\",\"imageUrl\":\"https://example.com/a.jpg\",\"stock\":4,\"available\":true}";
  mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
  String response=mvc.perform(post("/api/products").header("Authorization",admin()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  long id=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("id").asLong();
  mvc.perform(put("/api/products/"+id).header("Authorization",admin()).contentType(MediaType.APPLICATION_JSON).content(body.replace("Test Product A","Test Product B"))).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Test Product B"));
  mvc.perform(delete("/api/products/"+id).header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
  mvc.perform(get("/api/products/"+id)).andExpect(status().isNotFound());
  mvc.perform(get("/api/products/admin/inactive").header("Authorization","Bearer "+jwt("USER"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/products/admin/inactive").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=="+id+")]").exists());
  mvc.perform(patch("/api/products/"+id+"/reactivate").header("Authorization",admin())).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.stock").value(4));
  mvc.perform(get("/api/products/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Test Product B"));
 }
 @Test void userCanBrowseButCannotMutateCatalog()throws Exception{
  mvc.perform(get("/api/products").header("Authorization","Bearer "+jwt("USER"))).andExpect(status().isOk());
  String body="{\"name\":\"User Forbidden Product\",\"price\":10,\"category\":\"Test\",\"stock\":1}";
  mvc.perform(post("/api/products").header("Authorization","Bearer "+jwt("USER")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
 }
 @Test void reservationsAreAtomicAndIdempotentAndCanBeReleased(){
  Product p=repo.save(new Product(new ProductRequest("Concurrent Stock","test",new java.math.BigDecimal("9.99"),"Test",null,3,true)));
  String key=UUID.randomUUID().toString();org.assertj.core.api.Assertions.assertThat(service.reserve(p.getId(),2,key).status()).isEqualTo("RESERVED");
  org.assertj.core.api.Assertions.assertThat(service.reserve(p.getId(),2,key).status()).isEqualTo("RESERVED");
  org.assertj.core.api.Assertions.assertThatThrownBy(()->service.reserve(p.getId(),2,UUID.randomUUID().toString())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  service.settle(key,false);org.assertj.core.api.Assertions.assertThat(repo.findById(p.getId()).orElseThrow().getStock()).isEqualTo(3);
 }
 @Test void concurrentReservationsCannotOversell()throws Exception{
  Product p=repo.saveAndFlush(new Product(new ProductRequest("Race Stock","test",new java.math.BigDecimal("9.99"),"Test",null,5,true)));
  java.util.concurrent.CountDownLatch gate=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.Callable<Boolean> reserve=()->{gate.await();try{service.reserve(p.getId(),4,UUID.randomUUID().toString());return true;}catch(org.springframework.web.server.ResponseStatusException ex){return false;}};
  var a=pool.submit(reserve);var b=pool.submit(reserve);gate.countDown();int accepted=(a.get()?1:0)+(b.get()?1:0);pool.shutdownNow();
  org.assertj.core.api.Assertions.assertThat(accepted).isEqualTo(1);
  org.assertj.core.api.Assertions.assertThat(repo.findById(p.getId()).orElseThrow().getStock()).isEqualTo(1);
 }
 @Test void releaseAfterCommitRestocksExactlyOnce(){Product p=repo.save(new Product(new ProductRequest("Cancel Stock","test",new java.math.BigDecimal("9.99"),"Test",null,6,true)));String key=UUID.randomUUID().toString();service.reserve(p.getId(),2,key);org.assertj.core.api.Assertions.assertThat(service.settle(key,true).status()).isEqualTo("COMMITTED");org.assertj.core.api.Assertions.assertThat(repo.findById(p.getId()).orElseThrow().getStock()).isEqualTo(4);org.assertj.core.api.Assertions.assertThat(service.settle(key,false).status()).isEqualTo("RESTOCKED");service.settle(key,false);org.assertj.core.api.Assertions.assertThat(repo.findById(p.getId()).orElseThrow().getStock()).isEqualTo(6);}
 @Test void abandonedReservationsAreReleasedAndRestockedExactlyOnce(){Product p=repo.save(new Product(new ProductRequest("Expired Stock","test",new java.math.BigDecimal("9.99"),"Test",null,6,true)));String key=UUID.randomUUID().toString();service.reserve(p.getId(),2,key);java.time.Instant future=java.time.Instant.now().plusSeconds(5);org.assertj.core.api.Assertions.assertThat(service.reconcileExpiredReservations(future)).isPositive();org.assertj.core.api.Assertions.assertThat(repo.findById(p.getId()).orElseThrow().getStock()).isEqualTo(6);org.assertj.core.api.Assertions.assertThat(service.reconcileExpiredReservations(future)).isZero();org.assertj.core.api.Assertions.assertThat(service.settle(key,true).status()).isEqualTo("RELEASED");}
 @Test void errorsReturnJsonBody()throws Exception{
  mvc.perform(get("/api/products/999999")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Product 999999 not found"));
  mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Authentication required"));
  mvc.perform(post("/api/products").header("Authorization","Bearer "+jwt("USER")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("Insufficient permissions"));
 }
}
