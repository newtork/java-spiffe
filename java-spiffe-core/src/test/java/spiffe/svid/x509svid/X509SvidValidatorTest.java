package spiffe.svid.x509svid;

import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import spiffe.bundle.x509bundle.X509Bundle;
import spiffe.exception.BundleNotFoundException;
import spiffe.spiffeid.SpiffeId;
import spiffe.spiffeid.TrustDomain;
import spiffe.utils.X509CertificateTestUtils.CertAndKeyPair;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static spiffe.utils.X509CertificateTestUtils.createCertificate;
import static spiffe.utils.X509CertificateTestUtils.createRootCA;

public class X509SvidValidatorTest {

    List<X509Certificate> chain;
    CertAndKeyPair rootCa;
    CertAndKeyPair otherRootCa;
    CertAndKeyPair leaf;

    @BeforeEach
    void setUp() throws Exception {
        rootCa = createRootCA("C = US, O = SPIFFE", "spiffe://example.org" );
        val intermediate1 = createCertificate("C = US, O = SPIRE", "C = US, O = SPIFFE",  "spiffe://example.org/host", rootCa, true);
        val intermediate2 = createCertificate("C = US, O = SPIRE", "C = US, O = SPIRE",  "spiffe://example.org/host2", intermediate1, true);
        leaf = createCertificate("C = US, O = SPIRE", "C = US, O = SPIRE",  "spiffe://example.org/test", intermediate2, false);
        chain = Arrays.asList(leaf.getCertificate(), intermediate2.getCertificate(), intermediate1.getCertificate());
        otherRootCa = createRootCA("C = US, O = SPIFFE", "spiffe://example.org" );
    }

    @Test
    void testVerifyChain_chainCanBeVerifiedWithAuthorityInBundle() throws Exception {
        HashSet<X509Certificate> x509Authorities = new HashSet<>();
        x509Authorities.add(rootCa.getCertificate());
        x509Authorities.add(otherRootCa.getCertificate());

        val x509Bundle = new X509Bundle(TrustDomain.of("example.org"), x509Authorities);
        X509SvidValidator.verifyChain(chain, x509Bundle);
    }

    @Test
    void testVerifyChain_chainCannotBeVerifiedWithAuthorityInBundle_throwsCertificateException() throws Exception {
        HashSet<X509Certificate> x509Authorities = new HashSet<>();
        x509Authorities.add(otherRootCa.getCertificate());

        val x509Bundle = new X509Bundle(TrustDomain.of("example.org"), x509Authorities);
        try {
            X509SvidValidator.verifyChain(chain, x509Bundle);
            fail("exception is expected");
        } catch (CertificateException e) {
            assertEquals("Cert chain cannot be verified", e.getMessage());
        }
    }

    @Test
    void verifyChain_noBundleForTrustDomain_throwsBundleNotFoundException() throws Exception {
        HashSet<X509Certificate> x509Authorities = new HashSet<>();
        x509Authorities.add(otherRootCa.getCertificate());

        val x509Bundle = new X509Bundle(TrustDomain.of("other.org"), x509Authorities);

        try {
            X509SvidValidator.verifyChain(chain, x509Bundle);
            fail("Verify chain should have thrown validation exception");
        } catch (BundleNotFoundException e) {
            assertEquals("No X509 bundle found for trust domain example.org", e.getMessage());
        }
    }

    @Test
    void checkSpiffeId_givenASpiffeIdInTheListOfAcceptedIds_doesntThrowException() throws IOException, CertificateException, URISyntaxException {
        val spiffeId1 = SpiffeId.parse("spiffe://example.org/test");
        val spiffeId2 = SpiffeId.parse("spiffe://example.org/test2");

        val spiffeIdList = Arrays.asList(spiffeId1, spiffeId2);

        X509SvidValidator.verifySpiffeId(leaf.getCertificate(), () -> spiffeIdList);
    }

    @Test
    void checkSpiffeId_givenASpiffeIdNotInTheListOfAcceptedIds_throwsCertificateException() throws IOException, CertificateException, URISyntaxException {
        val spiffeId1 = SpiffeId.parse("spiffe://example.org/other1");
        val spiffeId2 = SpiffeId.parse("spiffe://example.org/other2");
        List<SpiffeId> spiffeIdList = Arrays.asList(spiffeId1, spiffeId2);

        try {
            X509SvidValidator.verifySpiffeId(leaf.getCertificate(), () -> spiffeIdList);
            fail("Should have thrown CertificateException");
        } catch (CertificateException e) {
            assertEquals("SPIFFE ID spiffe://example.org/test in X.509 certificate is not accepted", e.getMessage());
        }
    }

    @Test
    void checkSpiffeId_nullX509Certificate_throwsNullPointerException() throws CertificateException {
        try {
            X509SvidValidator.verifySpiffeId(null, () -> EMPTY_LIST);
            fail("should have thrown an exception");
        } catch (NullPointerException e) {
            assertEquals("x509Certificate is marked non-null but is null", e.getMessage());
        }
    }

    @Test
    void checkSpiffeId_nullAcceptedSpiffeIdsSuppplier_throwsNullPointerException() throws CertificateException, URISyntaxException, IOException {
        try {
            X509SvidValidator.verifySpiffeId(leaf.getCertificate(), null);
            fail("should have thrown an exception");
        } catch (NullPointerException e) {
            assertEquals("acceptedSpiffedIdsSupplier is marked non-null but is null", e.getMessage());
        }
    }

    @Test
    void verifyChain_nullChain_throwsNullPointerException() throws CertificateException, BundleNotFoundException {
        try {
            X509SvidValidator.verifyChain(null, new X509Bundle(TrustDomain.of("example.org")));
            fail("should have thrown an exception");
        } catch (NullPointerException e) {
            assertEquals("chain is marked non-null but is null", e.getMessage());
        }
    }

    @Test
    void verifyChain_nullBundleSource_throwsNullPointerException() throws CertificateException, BundleNotFoundException, URISyntaxException, IOException {
        try {
            X509SvidValidator.verifyChain(chain, null);
            fail("should have thrown an exception");
        } catch (NullPointerException e) {
            assertEquals("x509BundleSource is marked non-null but is null", e.getMessage());
        }
    }
}
