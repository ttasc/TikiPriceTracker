package com.tiki.client;

import com.tiki.common.CryptoUtils;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.security.PublicKey;

public class TestClient {
    public static void main(String[] args) {
        try (Socket socket = new Socket("localhost", 12345);
             DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // 1. Nhận Public Key của Server
            int pubKeyLen = in.readInt();
            byte[] pubKeyBytes = new byte[pubKeyLen];
            in.readFully(pubKeyBytes);
            PublicKey serverPubKey = CryptoUtils.getPublicKeyFromBytes(pubKeyBytes);

            // 2. Tạo AES Key và mã hóa bằng RSA Public Key
            SecretKey sessionKey = CryptoUtils.generateAESKey();
            byte[] encryptedAESKey = CryptoUtils.encryptRSA(sessionKey.getEncoded(), serverPubKey);
            
            // 3. Gửi AES Key đã mã hóa cho Server
            out.writeInt(encryptedAESKey.length);
            out.write(encryptedAESKey);
            out.flush();

            // 4. Gửi dữ liệu bảo mật
            String msg = "Chào Server, đây là dữ liệu bí mật!";
            out.writeUTF(CryptoUtils.encryptAES(msg, sessionKey));
            
            // 5. Nhận phản hồi
            String responseEnc = in.readUTF();
//            System.out.println(responseEnc);
            System.out.println("Server trả lời (giải mã): " + CryptoUtils.decryptAES(responseEnc, sessionKey));

        } catch (Exception e) { e.printStackTrace(); }
    }
}
