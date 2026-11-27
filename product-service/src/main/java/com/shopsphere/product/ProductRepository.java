package com.shopsphere.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByActiveTrue();
    List<Product> findByActiveFalseOrderByNameAsc();
    List<Product> findByActiveTrueAndNameContainingIgnoreCase(String name);
    List<Product> findByActiveTrueAndCategoryIgnoreCase(String category);
    List<Product> findByActiveTrueAndCategoryIgnoreCaseAndNameContainingIgnoreCase(String category,String name);
    Page<Product> findByActiveTrue(Pageable pageable);
    @Query("select p from Product p where p.active=true and (:category is null or lower(p.category)=lower(:category)) and (:q is null or lower(p.name) like lower(concat('%',:q,'%')))")
    Page<Product> searchPage(@Param("q") String q, @Param("category") String category, Pageable pageable);
    boolean existsByNameIgnoreCase(String name);
    java.util.Optional<Product> findBySeedKey(String seedKey);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update Product p set p.stock=p.stock-:quantity,p.version=p.version+1 where p.id=:id and p.active=true and p.available=true and p.stock>=:quantity")
    int reserveStock(@Param("id") Long id,@Param("quantity") int quantity);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update Product p set p.stock=p.stock+:quantity,p.version=p.version+1 where p.id=:id")
    int returnStock(@Param("id") Long id,@Param("quantity") int quantity);
}
