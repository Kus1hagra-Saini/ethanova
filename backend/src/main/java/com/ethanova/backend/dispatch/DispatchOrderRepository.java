package com.ethanova.backend.dispatch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DispatchOrderRepository extends JpaRepository<DispatchOrder, Long> {

    Optional<DispatchOrder> findByOrderNumber(String orderNumber);

    List<DispatchOrder> findByDestinationDepotIdAndStatus(Long depotId, DispatchStatus status);

    List<DispatchOrder> findByExpectedArrivalDateBetween(LocalDate from, LocalDate to);
}