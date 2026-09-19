package sketchware.plus.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;

public class SecurityUtils {

    public static String getKeystoreSignatureHash(String path, char[] password) {
        try {
            KeyStore ks = KeyStore.getInstance(path.endsWith(".jks") ? "JKS" : "BKS");
            try (FileInputStream fis = new FileInputStream(path)) {
                ks.load(fis, password);
            }
            
            Enumeration<String> aliases = ks.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = ks.getCertificate(alias);
                if (cert != null) {
                    return getCertificateHash(cert);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getCertificateHash(Certificate cert) throws Exception {
        byte[] encoded = cert.getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(encoded);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}
