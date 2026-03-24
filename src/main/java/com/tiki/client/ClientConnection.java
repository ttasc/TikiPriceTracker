package com.tiki.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.security.PublicKey;

import javax.crypto.SecretKey;

import com.tiki.common.CryptoUtils;

public class ClientConnection {
	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private SecretKey sessionKey;

	public void connect(String host, int port) throws Exception {
		socket = new Socket(host, port);
		in = new DataInputStream(socket.getInputStream());
		out = new DataOutputStream(socket.getOutputStream());

		// 1. Nhận Public Key RSA từ Server
		int pubKeyLen = in.readInt();
		byte[] pubKeyBytes = new byte[pubKeyLen];
		in.readFully(pubKeyBytes);
		PublicKey serverPubKey = CryptoUtils.getPublicKeyFromBytes(pubKeyBytes);

		// 2. Tạo AES Key và gửi cho Server (đã mã hóa RSA)
		sessionKey = CryptoUtils.generateAESKey();
		byte[] encryptedAESKey = CryptoUtils.encryptRSA(sessionKey.getEncoded(), serverPubKey);
		out.writeInt(encryptedAESKey.length);
		out.write(encryptedAESKey);
		out.flush();
	}

	public String sendRequest(String jsonRequest) throws Exception {
		// Mã hóa và gửi
		out.writeUTF(CryptoUtils.encryptAES(jsonRequest, sessionKey));
		out.flush();
		// Nhận và giải mã
		String encryptedResponse = in.readUTF();
		return CryptoUtils.decryptAES(encryptedResponse, sessionKey);
	}
}
