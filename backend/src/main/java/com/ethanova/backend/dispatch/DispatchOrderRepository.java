package com.ethanova.backend.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchOrderRepository extends JpaRepository<DispatchOrder, Long> {

    Optional<DispatchOrder> findByOrderNumber(String orderNumber);

    List<DispatchOrder> findByDestinationDepotIdAndStatus(Long depotId, DispatchStatus status);

    List<DispatchOrder> findByExpectedArrivalDateBetween(LocalDate from, LocalDate to);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * Counts orders created in a given calendar year+month, used to seed the
     * server-side order-number sequence (DO-YYYY-MM-<n>).
     */
    long countByOrderNumberStartingWith(String prefix);

    // ---------------------------------------------------------------------
    // Fetch variants for the read API — pull all three lazy FK targets in a
    // single query. Ordered by id desc so the newest orders surface first.
    // ---------------------------------------------------------------------

    @Query("""
            select o
              from DispatchOrder o
              join fetch o.supplier
              join fetch o.sourcePlant
              join fetch o.destinationDepot
             order by o.id desc
            """)
    List<DispatchOrder> findAllWithAssociations();

    @Query("""
            select o
              from DispatchOrder o
              join fetch o.supplier
              join fetch o.sourcePlant
              join fetch o.destinationDepot
             where o.orderNumber = :orderNumber
            """)
    Optional<DispatchOrder> findByOrderNumberWithAssociations(
            @org.springframework.data.repository.query.Param("orderNumber") String orderNumber
    );
}