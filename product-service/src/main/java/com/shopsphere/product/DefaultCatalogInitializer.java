package com.shopsphere.product;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
@Configuration class DefaultCatalogInitializer {
 @Bean ApplicationRunner seedCatalog(CatalogSeeder seeder,CatalogSeedMarkerRepository markers){return args->{try{seeder.seed();}catch(DataIntegrityViolationException e){if(!markers.existsById("default-catalog-v1"))throw e;}};}
}
@org.springframework.stereotype.Service class CatalogSeeder {
 private final ProductRepository products;private final CatalogSeedMarkerRepository markers;
 CatalogSeeder(ProductRepository products,CatalogSeedMarkerRepository markers){this.products=products;this.markers=markers;}
 @Transactional public void seed(){if(markers.existsById("default-catalog-v1"))return;
  List<Product> defaults=List.of(
   p("iphone-15","Apple iPhone 15","6.1-inch Super Retina XDR display, A16 Bionic chip and 128GB storage.",799.00,"Electronics","https://images.unsplash.com/photo-1592750475338-74b7b21085ab",18),
   p("galaxy-s24","Samsung Galaxy S24","Galaxy AI smartphone with vivid AMOLED display and 256GB storage.",849.99,"Electronics","https://images.unsplash.com/photo-1610945265064-0e34e5519bbf",20),
   p("macbook-air-m3","MacBook Air M3","13-inch lightweight laptop with Apple M3, 16GB memory and all-day battery.",1299.00,"Computers","https://images.unsplash.com/photo-1517336714731-489689fd1ca8",9),
   p("dell-inspiron","Dell Inspiron Laptop","15-inch everyday laptop with Intel Core i5, 16GB RAM and 512GB SSD.",749.00,"Computers","https://images.unsplash.com/photo-1496181133206-80ce9b88a853",12),
   p("sony-headphones","Sony Wireless Headphones","Over-ear Bluetooth headphones with active noise cancelling.",249.99,"Audio","https://images.unsplash.com/photo-1505740420928-5e560c06d30e",25),
   p("jbl-speaker","JBL Bluetooth Speaker","Portable waterproof speaker with rich sound and 12-hour battery.",89.95,"Audio","https://images.unsplash.com/photo-1608043152269-423dbba4e7e1",30),
   p("logitech-mouse","Logitech Wireless Mouse","Ergonomic wireless mouse with precise optical tracking.",39.99,"Accessories","https://images.unsplash.com/photo-1527814050087-3793815479db",40),
   p("mechanical-keyboard","Mechanical Keyboard","Compact mechanical keyboard with tactile switches and white backlight.",99.00,"Accessories","https://images.unsplash.com/photo-1587829741301-dc798b83add3",22),
   p("smart-watch","Smart Watch","Fitness and wellness smartwatch with GPS, heart-rate tracking and notifications.",179.00,"Wearables","https://images.unsplash.com/photo-1523275335684-37898b6baf30",17),
   p("samsung-4k-tv","Samsung 4K Smart TV","55-inch 4K UHD smart television with HDR and streaming apps.",699.00,"Home Entertainment","https://images.unsplash.com/photo-1593359677879-a4bb92f829d1",8),
   p("nike-running-shoes","Nike Running Shoes","Lightweight cushioned road-running shoes for everyday training.",119.00,"Fashion","https://images.unsplash.com/photo-1542291026-7eec264c27ff",26),
   p("casual-backpack","Casual Backpack","Water-resistant 22L daypack with padded laptop sleeve.",54.50,"Fashion","https://images.unsplash.com/photo-1553062407-98eeb64c6a62",32),
   p("cotton-tshirt","Cotton T-Shirt","Soft 100% cotton crew-neck T-shirt in a versatile everyday fit.",24.00,"Fashion","https://images.unsplash.com/photo-1521572163474-6864f9cf17ab",50),
   p("coffee-maker","Coffee Maker","Programmable 10-cup drip coffee maker with reusable filter.",79.99,"Home & Kitchen","https://images.unsplash.com/photo-1495474472287-4d71bcdd2085",14),
   p("air-fryer","Air Fryer","5.5-quart digital air fryer with preset cooking programs.",129.99,"Home & Kitchen","https://upload.wikimedia.org/wikipedia/commons/d/d3/Air_Fryer_2020.jpg",11));
  for(Product p:defaults)if(products.findBySeedKey(p.getSeedKey()).isEmpty())products.save(p);
  markers.save(new CatalogSeedMarker("default-catalog-v1"));
 }
 private Product p(String key,String name,String description,double price,String category,String image,int stock){return new Product(key,name,description,BigDecimal.valueOf(price),category,image,stock);}
}
