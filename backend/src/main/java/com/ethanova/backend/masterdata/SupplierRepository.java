package com.ethanova.backend.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findBySupplierCode(String supplierCode);

    List<Supplier> findByStateCodeAndActiveTrue(String stateCode);

    List<Supplier> findBySupplierType(SupplierType supplierType);
}