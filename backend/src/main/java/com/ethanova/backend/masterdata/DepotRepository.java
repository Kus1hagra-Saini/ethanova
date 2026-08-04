package com.ethanova.backend.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepotRepository extends JpaRepository<Depot, Long> {

    Optional<Depot> findByDepotCode(String depotCode);

    boolean existsByDepotCode(String depotCode);

    List<Depot> findByStateCodeAndActiveTrue(String stateCode);

    List<Depot> findByOmcCode(String omcCode);
}