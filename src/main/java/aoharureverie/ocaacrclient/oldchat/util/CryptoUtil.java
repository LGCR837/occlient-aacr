package aoharureverie.ocaacrclient.oldchat.util;

import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.spongycastle.jce.provider.BouncyCastleProvider;

public class CryptoUtil {
    private static final Object SESSION_LOCK = new Object();
    private static final int SECRET_SIZE = 32;
    private static String sessionId;
    private static byte[] sessionEncKey;
    private static byte[] sessionMacKey;
    private static Boolean ecdhSupported;
    private static final Object PROVIDER_LOCK = new Object();
    private static volatile boolean providerReady;

    public static class SessionKeys {
        public final byte[] encKey;
        public final byte[] macKey;

        SessionKeys(byte[] encKey, byte[] macKey) {
            this.encKey = encKey;
            this.macKey = macKey;
        }
    }

    public static class Handshake {
        private final PrivateKey privateKey;
        private final String publicKey;

        Handshake(PrivateKey privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public SessionKeys finish(String serverPublicKey) throws Exception {
            byte[] serverBytes = Base64.decode(serverPublicKey, Base64.NO_WRAP);
            KeyFactory factory = getEcKeyFactory();
            PublicKey serverKey = factory.generatePublic(new X509EncodedKeySpec(serverBytes));
            KeyAgreement agreement = getEcdhAgreement();
            agreement.init(privateKey);
            agreement.doPhase(serverKey, true);
            byte[] secret = agreement.generateSecret();
            secret = normalizeSecret(secret, SECRET_SIZE);
            byte[] encKey = sha256(concat(secret, "enc".getBytes("UTF-8")));
            byte[] macKey = sha256(concat(secret, "mac".getBytes("UTF-8")));
            return new SessionKeys(encKey, macKey);
        }
    }

    public static Handshake beginHandshake() throws Exception {
        KeyPairGenerator generator = getEcKeyPairGenerator();
        try {
            generator.initialize(new ECGenParameterSpec("secp256r1"));
        } catch (Exception e) {
            generator.initialize(new ECGenParameterSpec("prime256v1"));
        }
        KeyPair keyPair = generator.generateKeyPair();
        String pub = Base64.encodeToString(keyPair.getPublic().getEncoded(), Base64.NO_WRAP);
        return new Handshake(keyPair.getPrivate(), pub);
    }

    public static void setSession(String newSessionId, byte[] encKey, byte[] macKey) {
        synchronized (SESSION_LOCK) {
            sessionId = newSessionId;
            sessionEncKey = encKey != null ? encKey.clone() : null;
            sessionMacKey = macKey != null ? macKey.clone() : null;
        }
    }

    public static void clearSession() {
        synchronized (SESSION_LOCK) {
            sessionId = null;
            sessionEncKey = null;
            sessionMacKey = null;
        }
    }

    public static boolean hasSession() {
        synchronized (SESSION_LOCK) {
            return sessionId != null && sessionEncKey != null && sessionMacKey != null;
        }
    }

    public static boolean isEcdhSupported() {
        if (ecdhSupported != null) {
            return ecdhSupported.booleanValue();
        }
        boolean supported = false;
        try {
            KeyPairGenerator generator = getEcKeyPairGenerator();
            try {
                generator.initialize(new ECGenParameterSpec("secp256r1"));
            } catch (Exception e) {
                generator.initialize(new ECGenParameterSpec("prime256v1"));
            }
            getEcdhAgreement();
            supported = true;
        } catch (Exception e) {
            supported = false;
        }
        ecdhSupported = Boolean.valueOf(supported);
        return supported;
    }

    public static void ensureProvider() {
        if (providerReady) {
            return;
        }
        synchronized (PROVIDER_LOCK) {
            if (providerReady) {
                return;
            }
            try {
                if (Security.getProvider("SC") == null) {
                    Security.insertProviderAt(new BouncyCastleProvider(), 1);
                }
            } catch (Throwable ignored) {
            }
            providerReady = true;
        }
    }

    private static KeyPairGenerator getEcKeyPairGenerator() throws Exception {
        ensureProvider();
        try {
            return KeyPairGenerator.getInstance("EC", "SC");
        } catch (Exception e) {
            return KeyPairGenerator.getInstance("EC");
        }
    }

    private static KeyAgreement getEcdhAgreement() throws Exception {
        ensureProvider();
        try {
            return KeyAgreement.getInstance("ECDH", "SC");
        } catch (Exception e) {
            return KeyAgreement.getInstance("ECDH");
        }
    }

    private static KeyFactory getEcKeyFactory() throws Exception {
        ensureProvider();
        try {
            return KeyFactory.getInstance("EC", "SC");
        } catch (Exception e) {
            return KeyFactory.getInstance("EC");
        }
    }

    public static String getSessionId() {
        synchronized (SESSION_LOCK) {
            return sessionId;
        }
    }

    public static String encrypt(String plain) throws Exception {
        SessionKeys keys = getSessionKeys();
        if (keys == null) {
            throw new IllegalStateException("missing session keys");
        }
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.encKey, "AES"), new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes("UTF-8"));

        byte[] mac = hmac(keys.macKey, concat(iv, encrypted));
        JSONObject obj = new JSONObject();
        obj.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        obj.put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        obj.put("mac", Base64.encodeToString(mac, Base64.NO_WRAP));
        return obj.toString();
    }

    public static String decryptIfNeeded(String payload) {
        try {
            JSONObject obj = new JSONObject(payload);
            if (!obj.has("iv") || !obj.has("data") || !obj.has("mac")) {
                return null;
            }
            SessionKeys keys = getSessionKeys();
            if (keys == null) {
                return null;
            }
            byte[] iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP);
            byte[] data = Base64.decode(obj.getString("data"), Base64.NO_WRAP);
            byte[] mac = Base64.decode(obj.getString("mac"), Base64.NO_WRAP);

            byte[] expected = hmac(keys.macKey, concat(iv, data));
            if (!MessageDigest.isEqual(mac, expected)) {
                return null;
            }

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys.encKey, "AES"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(data);
            return new String(plain, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static SessionKeys getSessionKeys() {
        synchronized (SESSION_LOCK) {
            if (sessionEncKey == null || sessionMacKey == null) {
                return null;
            }
            return new SessionKeys(sessionEncKey.clone(), sessionMacKey.clone());
        }
    }

    private static byte[] sha256(byte[] input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(input);
        return md.digest();
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] normalizeSecret(byte[] secret, int size) {
        if (secret.length == size) {
            return secret;
        }
        byte[] out = new byte[size];
        if (secret.length > size) {
            System.arraycopy(secret, secret.length - size, out, 0, size);
        } else {
            System.arraycopy(secret, 0, out, size - secret.length, secret.length);
        }
        return out;
    }
}
