package com.tmp.warehouse.domain.repository;

import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReservationLinkId;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.ReservationTargetReference;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for informational {@link MaterialReservationLink} records.
 *
 * <p>Must not mutate stock positions or warehouse movements.
 */
public interface MaterialReservationLinkRepository {

    MaterialReservationLink create(MaterialReservationLink link);

    Optional<MaterialReservationLink> findById(MaterialReservationLinkId id);

    List<MaterialReservationLink> findByMaterial(MaterialReference material);

    List<MaterialReservationLink> findByTarget(ReservationTargetReference target);
}
