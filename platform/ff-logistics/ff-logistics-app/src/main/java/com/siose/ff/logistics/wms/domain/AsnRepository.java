package com.siose.ff.logistics.wms.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsnRepository extends JpaRepository<Asn, UUID> {

    /** The receiving plan: what arrives first comes first. */
    List<Asn> findAllByOrderByExpectedArrivalAscAsnNumberAsc();

    boolean existsByAsnNumber(String asnNumber);
}
