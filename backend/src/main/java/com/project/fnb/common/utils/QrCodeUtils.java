package com.project.fnb.common.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

public class QrCodeUtils {

    public static MultipartFile generateQrCodeImage(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height);

            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();

            return new ByteArrayMultipartFile(
                    pngData,
                    "qrcode",      
                    "qrcode.png",   
                    "image/png"    
            );
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo QR Code: " + e.getMessage());
        }
    }
}