package com.siose.ff.logistics.wms.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BinLocationRepository extends JpaRepository<BinLocation, UUID> {

    List<BinLocation> findAllByWarehouseIdOrderByCode(UUID warehouseId);

    boolean existsByWarehouseIdAndCode(UUID warehouseId, String code);
}
