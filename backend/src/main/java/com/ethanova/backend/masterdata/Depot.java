package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "depots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Depot extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "depot_code", nullable = false, unique = true, length = 50)
    private String depotCode;

    @Column(name = "depot_name", nullable = false, length = 200)
    private String depotName;

    @Column(name = "omc_code", nullable = false, length = 20)
    private String omcCode;

    @Column(name = "state_code", nullable = false, length = 10)
    private String stateCode;

    @Column(name = "storage_capacity_kl", nullable = false, precision = 15, scale = 3)
    private BigDecimal storageCapacityKl;

    @Column(name = "reorder_threshold_kl", precision = 15, scale = 3)
    private BigDecimal reorderThresholdKl;

    @Column(nullable = false)
    private Boolean active = true;
}
