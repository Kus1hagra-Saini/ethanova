package com.ethanova.backend.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductionPlantRepository extends JpaRepository<ProductionPlant, Long> {

    Optional<ProductionPlant> findByPlantCode(String plantCode);

    List<ProductionPlant> findBySupplierId(Long supplierId);
}