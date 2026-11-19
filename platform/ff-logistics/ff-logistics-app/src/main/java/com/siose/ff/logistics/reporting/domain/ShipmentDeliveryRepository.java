package com.siose.ff.logistics.reporting.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentDeliveryRepository extends JpaRepository<ShipmentDelivery, UUID> {

    boolean existsByShipmentId(UUID shipmentId);

    List<ShipmentDelivery> findAllByOrderByDeliveredAtDesc();
}
