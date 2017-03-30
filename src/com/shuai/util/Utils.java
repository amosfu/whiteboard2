package com.shuai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Created by Amos on 2017-03-08.
 */
public class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);
    public static KeyPairGenerator KEY_PAIR_GENERATOR;
    public static KeyFactory KEY_FACTORY;

    static {
        try {
            KEY_PAIR_GENERATOR = KeyPairGenerator.getInstance("DH");
            BigInteger p = new BigInteger("f460d489678f7ec903293517e9193fd156c821b3e2b027c644eb96aedc85a54c971468cea07df15e9ecda0e2ca062161add38b9aa8aefcbd7ac18cd05a6bfb1147aaa516a6df694ee2cb5164607c618df7c65e75e274ff49632c34ce18da534ee32cfc42279e0f4c29101e89033130058d7f77744dddaca541094f19c394d485", 16);
            BigInteger g = new BigInteger("9ce2e29b2be0ebfd7b3c58cfb0ee4e9004e65367c069f358effaf2a8e334891d20ff158111f54b50244d682b720f964c4d6234079d480fcc2ce66e0fa3edeb642b0700cd62c4c02a483c92d2361e41a23706332bd3a8aaed07fe53bba376cefbce12fa46265ad5ea5210a3d96f5260f7b6f29588f61a4798e40bdc75bbb2b457", 16);
            int l = 512;
            DHParameterSpec dhSpec = new DHParameterSpec(p, g, l);
            KEY_PAIR_GENERATOR.initialize(dhSpec);
            KEY_FACTORY = KeyFactory.getInstance("DH");
        } catch (Exception e) {
            logger.debug(e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getDBConnection(String dbStr, String dbUsr, String dbPwd) {
        Connection c = null;
        try {
            Class.forName("com.mysql.jdbc.Driver");
            c = DriverManager.getConnection(dbStr, dbUsr, dbPwd);

        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
        return c;
    }

    public static byte[] encryptJsonObject(Object input, byte[] key){
        byte[] encrypted = null;
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            String json = ow.writeValueAsString(input);
            MessageDigest sha3 = MessageDigest.getInstance("SHA-256");
            // generate 256bit AES key
            byte[] digestedKey = sha3.digest(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(shortenSecretKey(digestedKey, 128), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            encrypted = cipher.doFinal(json.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encrypted;
    }
    public static <T> T decryptJsonObject(byte[] input, byte[] key, Class<T> outputClass){
        T decryptedObject = null;
        try {
            MessageDigest sha3 = MessageDigest.getInstance("SHA-256");
            // generate 256bit AES key
            byte[] digestedKey = sha3.digest(key);
            SecretKeySpec secretKeySpec = new SecretKeySpec(shortenSecretKey(digestedKey, 128), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            String json = new String(cipher.doFinal(input));
            decryptedObject = new ObjectMapper().readValue(json, outputClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return decryptedObject;
    }
    /**
     * 1024 bit symmetric key size is so big for DES so we must shorten the key size. You can get first 8 longKey of the
     * byte array or can use a key factory
     *
     * @param longKey
     * @return
     */
    public static byte[] shortenSecretKey(final byte[] longKey, int newKeyLength) {
        try {
            // Use 8 bytes (64 bits) for DES, 6 bytes (48 bits) for Blowfish
            final byte[] shortenedKey = new byte[newKeyLength / 8];
            System.arraycopy(longKey, 0, shortenedKey, 0, shortenedKey.length);
            return shortenedKey;

            // Below lines can be more secure
            // final SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            // final DESKeySpec       desSpec    = new DESKeySpec(longKey);
            //
            // return keyFactory.generateSecret(desSpec).getEncoded();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getSHA256SecurePassword(String passwordToHash, String salt)
    {
        String generatedPassword = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] bytes = md.digest(passwordToHash.getBytes());
            StringBuilder sb = new StringBuilder();
            for(int i=0; i< bytes.length ;i++)
            {
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            generatedPassword = sb.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();
        }
        return generatedPassword;
    }

    //Add salt
    public static String getPasswordSalt() throws NoSuchAlgorithmException
    {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for(int i=0; i< salt.length ;i++)
        {
            sb.append(Integer.toString((salt[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }
    public static byte[] encodeObjectToJson(Object input) {
        byte[] json = new byte[]{};
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            json = ow.writeValueAsBytes(input);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    public static <T> T decodeJsonToObject(byte[] input, Class<T> outputClass) {
        T output = null;
        try {
            output = new ObjectMapper().readValue(input, outputClass);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return output;
    }
}
