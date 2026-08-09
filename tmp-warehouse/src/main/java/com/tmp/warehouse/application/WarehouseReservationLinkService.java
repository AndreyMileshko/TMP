package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReservationLinkId;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Creates and queries informational material reservation links (Specification §8).
 *
 * <p>Does not mutate Stock Position, does not create Warehouse Movement, and does not introduce
 * {@code RESERVED} stock state. Release of links is out of scope for v1.0.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected repository and clock.")
public final class WarehouseReservationLinkService {

    private final MaterialReservationLinkRepository links;
    private final Clock clock;

    public WarehouseReservationLinkService(
            MaterialReservationLinkRepository links, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates an informational link: material + order/production reference + quantity.
     */
    public MaterialReservationLink createLink(
            MaterialReference material,
            ReservationTargetReference target,
            StockQuantity quantity) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(quantity, "quantity");
        MaterialReservationLink link =
                MaterialReservationLink.create(
                        MaterialReservationLinkId.generate(),
                        material,
                        target,
                        quantity,
                        clock.instant());
        return links.create(link);
    }

    public Optional<MaterialReservationLink> findById(MaterialReservationLinkId id) {
        Objects.requireNonNull(id, "id");
        return links.findById(id);
    }

    public List<MaterialReservationLink> findByMaterial(MaterialReference material) {
        Objects.requireNonNull(material, "material");
        return links.findByMaterial(material);
    }

    public List<MaterialReservationLink> findByTarget(ReservationTargetReference target) {
        Objects.requireNonNull(target, "target");
        return links.findByTarget(target);
    }
}
