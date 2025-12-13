package com.project.fnb.common.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Utility class cung cấp các phương thức mã hóa/giải mã dữ liệu bằng AES (Advanced Encryption Standard).
 * 
 * <p>Class này được sử dụng để bảo vệ dữ liệu sensitive trong hệ thống FnB SaaS,
 * ví dụ như password, token, hoặc các thông tin cá nhân của user.</p>
 * 
 * <p><b>Encryption Algorithm:</b></p>
 * <ul>
 *   <li>Algorithm: AES (Advanced Encryption Standard)</li>
 *   <li>Mode: ECB (Electronic Codebook) - không sử dụng IV (không recommended cho production)</li>
 *   <li>Padding: PKCS5 (mặc định của Java Cipher)</li>
 *   <li>Key: Từ application.yml (app.security.aes-secret)</li>
 *   <li>Encoding: Base64 (để dễ truyền qua API)</li>
 * </ul>
 * 
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>Secret key được inject từ application.yml: app.security.aes-secret</li>
 *   <li>Key phải có độ dài hợp lệ (128, 192, hoặc 256 bits)</li>
 *   <li>@PostConstruct init() được gọi sau khi Spring inject dependencies</li>
 *   <li>Secret key được lưu vào static variable (singleton pattern)</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>
 * {@code
 * String plainText = "sensitive-data";
 * String encrypted = CryptoUtils.encrypt(plainText);
 * String decrypted = CryptoUtils.decrypt(encrypted);
 * 
 * // Null handling:
 * String result = CryptoUtils.encrypt(null); // returns null
 * }
 * </pre>
 * 
 * <p><b>Security Considerations:</b></p>
 * <ul>
 *   <li>AES là thuật toán mã hóa mạnh, khó bị crack</li>
 *   <li>ECB mode không an toàn cho dữ liệu dài (pattern recognition risk)</li>
 *   <li>Recommend sử dụng CBC mode với IV cho production</li>
 *   <li>Secret key nên được lưu trữ an toàn (environment variable, not in code)</li>
 *   <li>Key length nên ≥ 256 bits để bảo mật cao nhất</li>
 * </ul>
 * 
 * <p><b>Performance Notes:</b></p>
 * <ul>
 *   <li>Cipher object được tạo mới mỗi lần encrypt/decrypt</li>
 *   <li>Có thể cache Cipher nếu cần optimize performance (thread-safe required)</li>
 *   <li>Base64 encoding/decoding có overhead (không binary safe)</li>
 *   <li>Thích hợp cho dữ liệu nhỏ đến trung bình (< 1MB)</li>
 * </ul>
 * 
 * <p><b>Null Handling:</b></p>
 * <ul>
 *   <li>encrypt(null) → returns null (không throw exception)</li>
 *   <li>decrypt(null) → returns null (không throw exception)</li>
 *   <li>Hỗ trợ optional/nullable fields</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see javax.crypto.Cipher
 * @see java.util.Base64
 */
@Component
public class CryptoUtils {

    @Value("${app.security.aes-secret}")
    private String secret;

    private static String SECRET_KEY;

    /**
     * Khởi tạo AES secret key từ application properties.
     * 
     * <p>Phương thức này được gọi tự động sau khi Spring inject @Value dependency.
     * Secret key được lưu vào static variable để có thể sử dụng trong các static methods
     * (encrypt/decrypt).</p>
     * 
     * <p><b>Lifecycle:</b></p>
     * <ul>
     *   <li>@PostConstruct được gọi sau constructor</li>
     *   <li>@PostConstruct được gọi sau khi Spring inject tất cả dependencies</li>
     *   <li>Đảm bảo CryptoUtils sẵn sàng trước khi sử dụng</li>
     * </ul>
     * 
     * <p><b>Configuration Source:</b></p>
     * <ul>
     *   <li>Từ application.yml: app.security.aes-secret</li>
     *   <li>Ví dụ: app.security.aes-secret: "your-16-32-byte-secret-key"</li>
     *   <li>Key phải có độ dài 16, 24, hoặc 32 bytes (128, 192, 256 bits)</li>
     * </ul>
     * 
     * <p><b>Static Variable Pattern:</b></p>
     * <ul>
     *   <li>SECRET_KEY là static \ để encrypt/decrypt có thể là static methods</li>
     *   <li>Singleton pattern - chỉ init một lần khi Spring startup</li>
     *   <li>Thread-safe vì chỉ write một lần (startup), nhiều thread có thể read</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li>Nếu app.security.aes-secret không được set, Spring sẽ throw PropertyRequiredException</li>
     *   <li>Nếu secret có độ dài không hợp lệ, encrypt/decrypt sẽ throw exception lúc runtime</li>
     *   <li>Recommend kiểm tra secret length trong init() (future improvement)</li>
     * </ul>
     * 
     * @throws IllegalArgumentException nếu secret key có độ dài không hợp lệ (future)
     * @see org.springframework.beans.factory.annotation.Value
     * @see jakarta.annotation.PostConstruct
     */
    @PostConstruct
    public void init() {
        SECRET_KEY = secret;
    }

    private static final String ALGORITHM = "AES";

    /**
     * Mã hóa dữ liệu plaintext bằng AES encryption.
     * 
     * <p>Phương thức này nhận plaintext string, mã hóa bằng AES algorithm,
     * và trả về Base64-encoded ciphertext (an toàn để truyền qua API/JSON).</p>
     * 
     * <p><b>Quy trình mã hóa:</b></p>
     * <ol>
     *   <li>Kiểm tra nếu data là null → return null (null-safe)</li>
     *   <li>Tạo SecretKeySpec từ SECRET_KEY (static variable)</li>
     *   <li>Tạo Cipher instance cho AES algorithm</li>
     *   <li>Init Cipher với ENCRYPT_MODE và key</li>
     *   <li>Mã hóa plaintext byte array</li>
     *   <li>Encode ciphertext bằng Base64 (để truyền qua network/API)</li>
     *   <li>Trả về Base64 string</li>
     * </ol>
     * 
     * <p><b>Input/Output:</b></p>
     * <ul>
     *   <li>Input: plaintext string (có thể null)</li>
     *   <li>Output: Base64-encoded encrypted string</li>
     *   <li>Ví dụ: "hello" → "FZSc8C/SYTX3fj9q8tK+Cg=="</li>
     * </ul>
     * 
     * <p><b>Null Handling:</b></p>
     * <ul>
     *   <li>Nếu data == null → return null</li>
     *   <li>Không throw exception cho null input</li>
     *   <li>Hỗ trợ optional/nullable fields</li>
     * </ul>
     * 
     * <p><b>Encoding:</b></p>
     * <ul>
     *   <li>Input text được encode bằng UTF-8 (default)</li>
     *   <li>Output Base64 (ASCII-safe) - có thể truyền qua JSON/URL</li>
     *   <li>Base64 expand size ~33% (nhưng lossless)</li>
     * </ul>
     * 
     * <p><b>Security Notes:</b></p>
     * <ul>
     *   <li>AES là mã hóa đối xứng mạnh</li>
     *   <li>ECB mode (hiện tại) có pattern recognition risk</li>
     *   <li>Recommend sử dụng CBC/GCM mode với IV cho production</li>
     *   <li>Secret key phải bảo mật cao (store in environment variable)</li>
     * </ul>
     * 
     * <p><b>Performance:</b></p>
     * <ul>
     *   <li>Cipher instance được tạo mới mỗi lần (slight overhead)</li>
     *   <li>Base64 encoding có cost ~1-5% tùy data size</li>
     *   <li>Thích hợp cho dữ liệu nhỏ đến trung bình</li>
     * </ul>
     * 
     * @param data Plaintext string cần mã hóa (nullable)
     * 
     * @return Base64-encoded encrypted string, hoặc null nếu input là null
     * 
     * @throws RuntimeException nếu xảy ra lỗi mã hóa (Cipher initialization, encryption failure, v.v.)
     * 
     * @see javax.crypto.Cipher
     * @see java.util.Base64#getEncoder()
     */
    public static String encrypt(String data) {
        if (data == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }

    /**
     * Giải mã dữ liệu encrypted bằng AES decryption.
     * 
     * <p>Phương thức này nhận Base64-encoded ciphertext, giải mã bằng AES algorithm,
     * và trả về plaintext string gốc.</p>
     * 
     * <p><b>Quy trình giải mã:</b></p>
     * <ol>
     *   <li>Kiểm tra nếu encryptedData là null → return null (null-safe)</li>
     *   <li>Tạo SecretKeySpec từ SECRET_KEY (static variable)</li>
     *   <li>Tạo Cipher instance cho AES algorithm</li>
     *   <li>Init Cipher với DECRYPT_MODE và key</li>
     *   <li>Decode Base64 string thành byte array</li>
     *   <li>Giải mã byte array</li>
     *   <li>Convert byte array thành plaintext string</li>
     *   <li>Trả về plaintext</li>
     * </ol>
     * 
     * <p><b>Input/Output:</b></p>
     * <ul>
     *   <li>Input: Base64-encoded encrypted string (từ encrypt method)</li>
     *   <li>Output: plaintext string (gốc ban đầu)</li>
     *   <li>Ví dụ: "FZSc8C/SYTX3fj9q8tK+Cg==" → "hello"</li>
     * </ul>
     * 
     * <p><b>Null Handling:</b></p>
     * <ul>
     *   <li>Nếu encryptedData == null → return null</li>
     *   <li>Không throw exception cho null input</li>
     *   <li>Hỗ trợ optional/nullable fields</li>
     * </ul>
     * 
     * <p><b>Key Matching:</b></p>
     * <ul>
     *   <li>Secret key PHẢI giống với key được sử dụng trong encrypt()</li>
     *   <li>Nếu key khác nhau, decryption sẽ fail (BadPaddingException)</li>
     *   <li>Không thể giải mã dữ liệu mã hóa bởi key khác</li>
     * </ul>
     * 
     * <p><b>Encoding:</b></p>
     * <ul>
     *   <li>Input Base64 (ASCII-safe) được decode thành binary</li>
     *   <li>Binary được giải mã thành plaintext UTF-8</li>
     *   <li>Output string là plaintext gốc</li>
     * </ul>
     * 
     * <p><b>Security Notes:</b></p>
     * <ul>
     *   <li>AES là mã hóa đối xứng mạnh (reversible với key đúng)</li>
     *   <li>ECB mode (hiện tại) có pattern recognition risk</li>
     *   <li>Recommend sử dụng CBC/GCM mode với IV cho production</li>
     *   <li>Secret key phải bảo mật cao</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li>BadPaddingException: key sai hoặc dữ liệu corrupt</li>
     *   <li>IllegalArgumentException: Base64 input invalid</li>
     *   <li>Tất cả exception được wrap thành RuntimeException</li>
     * </ul>
     * 
     * <p><b>Performance:</b></p>
     * <ul>
     *   <li>Cipher instance được tạo mới mỗi lần (slight overhead)</li>
     *   <li>Base64 decoding có cost ~1-5% tùy data size</li>
     *   <li>Thích hợp cho dữ liệu nhỏ đến trung bình</li>
     * </ul>
     * 
     * @param encryptedData Base64-encoded encrypted string từ encrypt() (nullable)
     * 
     * @return Plaintext string (gốc ban đầu), hoặc null nếu input là null
     * 
     * @throws RuntimeException nếu xảy ra lỗi giải mã (Cipher initialization, decryption failure, key mismatch, v.v.)
     * 
     * @see javax.crypto.Cipher
     * @see java.util.Base64#getDecoder()
     * @see #encrypt(String)
     */
    public static String decrypt(String encryptedData) {
        if (encryptedData == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }
}