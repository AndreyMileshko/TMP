package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReservationLinkId;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseReservationLinkServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T12:30:00Z"), ZoneOffset.UTC);

    private InMemoryLinkRepository links;
    private WarehouseReservationLinkService service;

    @BeforeEach
    void setUp() {
        links = new InMemoryLinkRepository();
        service = new WarehouseReservationLinkService(links, CLOCK);
    }

    @Test
    void createLinkStoresInformationalBindingWithoutStockSideEffects() {
        MaterialReference material = MaterialReference.legacyArticle("VEKA 103.211 WHITE");
        ReservationTargetReference order = ReservationTargetReference.order("26096190");

        MaterialReservationLink created =
                service.createLink(material, order, StockQuantity.of(200L));

        assertEquals(material, created.material());
        assertEquals(order, created.target());
        assertEquals(StockQuantity.of(200L), created.quantity());
        assertEquals(CLOCK.instant(), created.createdAt());
        assertEquals(created, service.findById(created.id()).orElseThrow());
    }

    @Test
    void findByTargetReturnsOrderBinding() {
        MaterialReference material = MaterialReference.legacyArticle("ALU-6060");
        ReservationTargetReference order = ReservationTargetReference.order("ORD-100");
        service.createLink(material, order, StockQuantity.of(50L));
        service.createLink(
                MaterialReference.legacyArticle("OTHER"),
                ReservationTargetReference.order("ORD-OTHER"),
                StockQuantity.of(10L));

        List<MaterialReservationLink> found = service.findByTarget(order);

        assertEquals(1, found.size());
        assertEquals(material, found.get(0).material());
        assertEquals(StockQuantity.of(50L), found.get(0).quantity());
        assertEquals(order, found.get(0).target());
    }

    @Test
    void findByMaterialReturnsAllLinksForMaterial() {
        MaterialReference material = MaterialReference.legacyArticle("VEKA 103.211 WHITE");
        service.createLink(
                material, ReservationTargetReference.order("A"), StockQuantity.of(100L));
        service.createLink(
                material,
                ReservationTargetReference.productionDemand("PD-1"),
                StockQuantity.of(40L));

        List<MaterialReservationLink> found = service.findByMaterial(material);

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(link -> link.material().equals(material)));
    }

    private static final class InMemoryLinkRepository implements MaterialReservationLinkRepository {
        private final Map<MaterialReservationLinkId, MaterialReservationLink> store =
                new ConcurrentHashMap<>();

        @Override
        public MaterialReservationLink create(MaterialReservationLink link) {
            store.put(link.id(), link);
            return link;
        }

        @Override
        public Optional<MaterialReservationLink> findById(MaterialReservationLinkId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<MaterialReservationLink> findByMaterial(MaterialReference material) {
            return store.values().stream()
                    .filter(link -> link.material().equals(material))
                    .toList();
        }

        @Override
        public List<MaterialReservationLink> findByTarget(ReservationTargetReference target) {
            List<MaterialReservationLink> result = new ArrayList<>();
            for (MaterialReservationLink link : store.values()) {
                if (link.target().equals(target)) {
                    result.add(link);
                }
            }
            return result;
        }
    }
}
