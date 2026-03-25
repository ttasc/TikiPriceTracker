package com.tiki.common;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Utility class for Hybrid Encryption (RSA + AES). Standardized Logging:
 * [SECURITY]
 */
public class CryptoUtils {

	/**
	 * Generates a 2048-bit RSA Key Pair. Typically used by the Server once at
	 * startup.
	 */
	public static KeyPair generateRSAKeyPair() throws Exception {
		System.out.println("[SECURITY] Generating new RSA 2048-bit key pair...");
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	/**
	 * Encrypts data using an RSA Public Key.
	 */
	public static byte[] encryptRSA(byte[] data, PublicKey publicKey) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, publicKey);
		return cipher.doFinal(data);
	}

	/**
	 * Decrypts data using an RSA Private Key.
	 */
	public static byte[] decryptRSA(byte[] data, PrivateKey privateKey) throws Exception {
		Cipher cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.DECRYPT_MODE, privateKey);
		return cipher.doFinal(data);
	}

	/**
	 * Generates a random AES-128 Session Key. Typically used by the Client for each
	 * session.
	 */
	public static SecretKey generateAESKey() throws Exception {
		System.out.println("[SECURITY] Generating random AES-128 session key...");
		KeyGenerator keyGen = KeyGenerator.getInstance("AES");
		keyGen.init(128);
		return keyGen.generateKey();
	}

	/**
	 * Encrypts a String using AES.
	 */
	public static String encryptAES(String data, SecretKey secretKey) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		byte[] encryptedBytes = cipher.doFinal(data.getBytes());
		return Base64.getEncoder().encodeToString(encryptedBytes);
	}

	/**
	 * Decrypts a Base64 encoded String using AES.
	 */
	public static String decryptAES(String encryptedData, SecretKey secretKey) throws Exception {
		Cipher cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
		return new String(cipher.doFinal(decodedBytes));
	}

	/**
	 * Converts raw bytes into an RSA PublicKey object.
	 */
	public static PublicKey getPublicKeyFromBytes(byte[] keyBytes) throws Exception {
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
	}
}
