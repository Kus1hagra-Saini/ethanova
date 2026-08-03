package com.ethanova.backend.inventory;

import com.ethanova.backend.common.audit.BaseAuditableEntity;
import com.ethanova.backend.dispatch.EthanolGrade;
import com.ethanova.backend.masterdata.Depot;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "inventory",
       uniqueConstraints = @UniqueConstraint(name = "uk_inventory_depot_grade",
                                             columnNames = {"depot_id", "ethanol_grade"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;

    @Enumerated(EnumType.STRING)
    @Column(name = "ethanol_grade", nullable = false, length = 20)
    private EthanolGrade ethanolGrade;

    @Column(name = "current_stock_kl", nullable = false, precision = 15, scale = 3)
    private BigDecimal currentStockKl;

    @Column(name = "max_capacity_kl", nullable = false, precision = 15, scale = 3)
    private BigDecimal maxCapacityKl;

    @Column(name = "reorder_level_kl", precision = 15, scale = 3)
    private BigDecimal reorderLevelKl;

    @Column(name = "last_updated_at", nullable = false)
    private OffsetDateTime lastUpdatedAt;
}