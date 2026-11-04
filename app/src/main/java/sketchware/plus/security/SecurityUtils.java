package sketchware.plus.security;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;

public class SecurityUtils {

    public static String getKeystoreSignatureHash(String path, char[] password) {
        return getKeystoreSignatureHash(path, password, null);
    }

    public static String getKeystoreSignatureHash(String path, char[] password, String targetAlias) {
        try {
            java.security.Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
            KeyStore ks = kellinwood.security.zipsigner.optional.KeyStoreFileManager.loadKeyStore(path, password);
            
            Certificate cert = null;
            if (targetAlias != null && !targetAlias.trim().isEmpty() && ks.containsAlias(targetAlias)) {
                cert = ks.getCertificate(targetAlias);
            }
            if (cert == null) {
                Enumeration<String> aliases = ks.aliases();
                if (aliases.hasMoreElements()) {
                    cert = ks.getCertificate(aliases.nextElement());
                }
            }
            if (cert != null) {
                return getCertificateHash(cert);
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
