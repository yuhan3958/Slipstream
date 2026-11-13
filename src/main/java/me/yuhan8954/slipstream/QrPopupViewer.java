package me.yuhan8954.slipstream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class QrPopupViewer {

    public static void showQrCode(String url) {
        if (url == null || url.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null, "유효한 링크 주소가 전달되지 않았습니다.", "오류", JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("QR 코드 보기");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(350, 400);
            frame.setLocationRelativeTo(null); // 화면 중앙 배치
            frame.setLayout(new BorderLayout(10, 10));

            try {
                BufferedImage qrImage = createQrImage(url, 300, 300);
                JLabel qrLabel = new JLabel(new ImageIcon(qrImage));

                JLabel urlLabel = new JLabel(url, SwingConstants.CENTER);
                urlLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 15, 10));

                frame.add(qrLabel, BorderLayout.CENTER);
                frame.add(urlLabel, BorderLayout.SOUTH);
                frame.setVisible(true);

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "QR 코드 생성 실패: " + e.getMessage(), "에러", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    static BufferedImage createQrImage(String text, int width, int height) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}
