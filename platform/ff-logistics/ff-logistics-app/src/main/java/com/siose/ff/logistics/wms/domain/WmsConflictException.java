package com.siose.ff.logistics.wms.domain;

import com.siose.ff.base.web.ConflictException;

/**
 * A request that clashes with what the warehouse already holds: answered with 409 and a
 * problem type per case ({@code urn:siose-ff:problem:<slug>}), so a client can tell them apart.
 */
public class WmsConflictException extends ConflictException {

    private final String slug;
    private final String title;

    private WmsConflictException(String slug, String title, String message) {
        super(message);
        this.slug = slug;
        this.title = title;
    }

    public static WmsConflictException warehouseCodeTaken(String code) {
        return new WmsConflictException("warehouse-code-taken", "Warehouse Code Taken",
                "A warehouse with code " + code + " already exists");
    }

    public static WmsConflictException warehouseGlnTaken(String gln) {
        return new WmsConflictException("warehouse-gln-taken", "Warehouse GLN Taken",
                "GLN " + gln + " already belongs to another warehouse");
    }

    public static WmsConflictException binCodeTaken(String code) {
        return new WmsConflictException("bin-code-taken", "Bin Location Code Taken",
                "This warehouse already has a bin location " + code);
    }

    public static WmsConflictException asnNumberTaken(String asnNumber) {
        return new WmsConflictException("asn-number-taken", "ASN Number Taken",
                "An ASN with number " + asnNumber + " already exists");
    }

    @Override
    public String problemSlug() {
        return slug;
    }

    @Override
    public String problemTitle() {
        return title;
    }
}
