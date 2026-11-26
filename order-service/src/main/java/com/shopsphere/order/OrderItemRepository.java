package com.shopsphere.order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
public interface OrderItemRepository extends JpaRepository<OrderItem,Long>{
 @Query("select i from OrderItem i join fetch i.order o where i.reservationStatus in :statuses")
 List<OrderItem> findForReconciliation(@Param("statuses") Collection<String> statuses);
 @Query("select i from OrderItem i where i.order.id=:orderId and i.reservationStatus in :statuses")
 List<OrderItem> findForOrder(@Param("orderId") Long orderId,@Param("statuses") Collection<String> statuses);
 @Modifying(clearAutomatically=true,flushAutomatically=true)
 @Query("update OrderItem i set i.reservationStatus=:next where i.reservationId=:id and i.reservationStatus=:current")
 int transitionReservation(@Param("id") String id,@Param("current") String current,@Param("next") String next);
}
