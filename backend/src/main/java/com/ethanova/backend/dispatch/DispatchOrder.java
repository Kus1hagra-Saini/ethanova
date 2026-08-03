package com.ethanova.backend.dispatch;

import com.ethanova.backend.common.audit.BaseAuditableEntity;
import com.ethanova.backend.masterdata.Depot;
import com.ethanova.backend.masterdata.ProductionPlant;
import com.ethanova.backend.masterdata.Supplier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "dispatch_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchOrder extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_plant_id", nullable = false)
    private ProductionPlant sourcePlant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_depot_id", nullable = false)
    private Depot destinationDepot;

    @Enumerated(EnumType.STRING)
    @Column(name = "ethanol_grade", nullable = false, length = 20)
    private EthanolGrade ethanolGrade;

    @Column(name = "quantity_kl", nullable = false, precision = 15, scale = 3)
    private BigDecimal quantityKl;

    @Column(name = "unit_price_inr_per_l", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitPriceInrPerL;

    @Column(name = "total_amount_inr", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmountInr;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_arrival_date", nullable = false)
    private LocalDate expectedArrivalDate;

    @Column(name = "actual_arrival_at")
    private OffsetDateTime actualArrivalAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DispatchStatus status = DispatchStatus.DRAFT;
}