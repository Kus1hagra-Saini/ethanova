package com.ethanova.backend.inventory;

import com.ethanova.backend.dispatch.EthanolGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByDepotIdAndEthanolGrade(Long depotId, EthanolGrade grade);

    List<Inventory> findByDepotId(Long depotId);
}