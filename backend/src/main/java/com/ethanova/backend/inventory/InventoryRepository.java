package com.ethanova.backend.inventory;

import com.ethanova.backend.dispatch.EthanolGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Existing derived query — used by write flows (e.g., dispatch orders in Milestone 3.6).
     * Depot is lazy here by design; callers that need depot data should use the fetch variants below.
     */
    Optional<Inventory> findByDepotIdAndEthanolGrade(Long depotId, EthanolGrade grade);

    List<Inventory> findByDepotId(Long depotId);

    // ---------------------------------------------------------------------
    // Fetch variants for the read API — JOIN FETCH the depot in one query to
    // avoid the N+1 pattern when mapping to InventoryResponse.
    // ---------------------------------------------------------------------

    @Query("select i from Inventory i join fetch i.depot")
    List<Inventory> findAllWithDepot();

    @Query("select i from Inventory i join fetch i.depot d where d.depotCode = :depotCode")
    List<Inventory> findByDepotCodeWithDepot(@Param("depotCode") String depotCode);

    @Query("""
            select i
              from Inventory i
              join fetch i.depot d
             where d.depotCode = :depotCode
               and i.ethanolGrade = :grade
            """)
    Optional<Inventory> findByDepotCodeAndGradeWithDepot(
            @Param("depotCode") String depotCode,
            @Param("grade") EthanolGrade grade
    );
}