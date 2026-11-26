package com.shopsphere.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
 Page<Order> findByUserId(Long userId,Pageable pageable);
 Optional<Order> findByCheckoutKey(String checkoutKey);
 long countByStatus(OrderStatus status);
 @Query("select coalesce(sum(o.totalPrice),0) from Order o where o.status=:status") BigDecimal revenueFor(@Param("status") OrderStatus status);
 @Query("select count(o) from Order o where o.status<>:delivered and o.status<>:cancelled") long countPending(@Param("delivered") OrderStatus delivered,@Param("cancelled") OrderStatus cancelled);
 @Query("select o from Order o where (:userId is null or o.userId=:userId) and (:filterUser is null or o.userId=:filterUser) and (:status is null or o.status=:status)") Page<Order> search(@Param("userId")Long userId,@Param("filterUser")Long filterUser,@Param("status")OrderStatus status,Pageable pageable);
 @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true,flushAutomatically=true)
 @Query("update Order o set o.status=:next,o.updatedAt=:updated where o.id=:id and o.status=:current") int transition(@Param("id")Long id,@Param("current")OrderStatus current,@Param("next")OrderStatus next,@Param("updated")java.time.Instant updated);
}
