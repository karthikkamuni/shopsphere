package com.shopsphere.product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;import org.springframework.data.jpa.repository.Query;import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.Instant;
public interface StockReservationRepository extends JpaRepository<StockReservation,String> {
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update StockReservation r set r.status='COMMITTED' where r.id=:id and r.status='RESERVED'") int markCommitted(@Param("id")String id);
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update StockReservation r set r.status='RELEASED' where r.id=:id and r.status='RESERVED'") int markReleased(@Param("id")String id);
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update StockReservation r set r.status='RESTOCKED' where r.id=:id and r.status='COMMITTED'") int markRestocked(@Param("id")String id);
 Page<StockReservation> findByStatusAndCreatedAtBefore(String status,Instant createdAt,Pageable pageable);
 @Modifying(clearAutomatically=true,flushAutomatically=true) @Query("update StockReservation r set r.status='RELEASED' where r.id=:id and r.status='RESERVED' and r.createdAt<:cutoff") int releaseExpired(@Param("id")String id,@Param("cutoff")Instant cutoff);
}
