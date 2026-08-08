package com.min.edu.admission.support;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AdmissionQrImageGenerator {
    private static final int QR_SIZE = 300;
    private static final int QR_MARGIN = 1;
    private static final String PNG_FORMAT = "PNG";

    public byte[] generate(String qrToken) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                qrToken,
                BarcodeFormat.QR_CODE,
                QR_SIZE,
                QR_SIZE,
                hints()
            );
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, PNG_FORMAT, outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException exception) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_QR_IMAGE_GENERATION_FAILED);
        }
    }

    private Map<EncodeHintType, Object> hints() {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QR_MARGIN);
        return hints;
    }
}
