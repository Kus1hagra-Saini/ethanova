package com.ethanova.backend.masterdata;

import com.ethanova.backend.common.audit.BaseAuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "production_plants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionPlant extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plant_code", nullable = false, unique = true, length = 50)
    private String plantCode;

    @Column(name = "plant_name", nullable = false, length = 200)
    private String plantName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "capacity_kl_per_day", nullable = false, precision = 15, scale = 3)
    private BigDecimal capacityKlPerDay;

    @Column(name = "state_code", nullable = false, length = 10)
    private String stateCode;

    @Column(nullable = false)
    private Boolean active = true;
}