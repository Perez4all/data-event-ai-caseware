package com.caseware.util;

import com.caseware.model.Cases;
import com.caseware.model.Customer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilTest {

    @Test
    void schemaFingerprint_returnsDeterministicHash() {
        String first = HashUtil.schemaFingerprint(Customer.class);
        String second = HashUtil.schemaFingerprint(Customer.class);
        assertEquals(first, second, "Same class must always produce the same fingerprint");
    }

    @Test
    void schemaFingerprint_returnsValidSha256HexString() {
        String hash = HashUtil.schemaFingerprint(Customer.class);
        assertNotNull(hash);
        assertEquals(64, hash.length(), "SHA-256 hex digest must be 64 chars");
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "Must be a lowercase hex string");
    }

    @Test
    void schemaFingerprint_differsBetweenEntities() {
        String customerHash = HashUtil.schemaFingerprint(Customer.class);
        String casesHash = HashUtil.schemaFingerprint(Cases.class);
        assertNotEquals(customerHash, casesHash, "Different entities must have different fingerprints");
    }

    @Test
    void schemaFingerprint_matchesKnownCustomerValue() {
        String hash = HashUtil.schemaFingerprint(Customer.class);
        assertEquals("3fda1462b573d1b21d6b3a853a5311828f5a16dd72d912e29c1bc5d8c63ff0ad", hash);
    }

    @Test
    void schemaFingerprint_matchesKnownCasesValue() {
        String hash = HashUtil.schemaFingerprint(Cases.class);
        assertEquals("001a11d0b0167f2a482c7300d68cdeb7a0fc8a0c16e76c877225514e791f70e5", hash);
    }
}

