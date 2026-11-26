package com.shopsphere.product;
import jakarta.persistence.*;
@Entity @Table(name="catalog_seed_markers") class CatalogSeedMarker {
 @Id @Column(length=80) private String seedId;
 protected CatalogSeedMarker(){} CatalogSeedMarker(String id){seedId=id;}
}
