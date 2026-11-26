package com.shopsphere.product;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="products", indexes={@Index(name="idx_products_category",columnList="category"),@Index(name="idx_products_name",columnList="name")})
public class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=150) private String name;
    @Column(length=1000) private String description;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal price;
    @Column(nullable=false,length=80) private String category;
    @Column(length=1000) private String imageUrl;
    @Column(nullable=false) private int stock;
    @Column(nullable=false) private boolean available=true;
    @Column(nullable=false) private boolean active=true;
    @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Version private long version;
    @Column(unique=true,length=80) private String seedKey;
    protected Product() {}
    public Product(ProductRequest r) { update(r); }
    public Product(String seedKey,String name,String description,BigDecimal price,String category,String imageUrl,int stock){this.seedKey=seedKey;this.name=name;this.description=description;this.price=price;this.category=category;this.imageUrl=imageUrl;this.stock=stock;this.available=true;this.active=true;}
    public void update(ProductRequest r) { name=r.name().trim(); description=r.description(); price=r.price(); category=r.category()==null||r.category().isBlank()?"General":r.category().trim(); imageUrl=r.imageUrl(); stock=r.stock(); available=r.available()==null||r.available(); active=true; }
    public void setActive(boolean value) { active=value; }
    public void setStock(int value) { stock=value; }
    public void setAvailable(boolean value) { available=value; }
    public Long getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public BigDecimal getPrice(){return price;} public String getCategory(){return category;} public String getImageUrl(){return imageUrl;}
    public int getStock(){return stock;} public boolean isAvailable(){return available && active && stock>0;}
    public boolean isActive(){return active;} public Instant getCreatedAt(){return createdAt;} public long getVersion(){return version;}
    public String getSeedKey(){return seedKey;}
}
