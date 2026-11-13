package me.yuhan8954.slipstream;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Controls the running local Slipstream server.
 */
public final class SlipstreamControlWindow {
    private final List<String> addresses;
    private final Path downloadDirectory;
    private final Runnable stopServer;
    private final JLabel qrLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel urlLabel = new JLabel("", SwingConstants.CENTER);

    private SlipstreamControlWindow(List<String> addresses, Path downloadDirectory, Runnable stopServer) {
        this.addresses = addresses;
        this.downloadDirectory = downloadDirectory;
        this.stopServer = stopServer;
    }

    public static void show(List<String> addresses, Path downloadDirectory, Runnable stopServer) {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("At least one local address is required.");
        }

        SwingUtilities.invokeLater(() -> new SlipstreamControlWindow(
                List.copyOf(addresses), downloadDirectory, stopServer
        ).showWindow());
    }

    private void showWindow() {
        JFrame frame = new JFrame("Slipstream Control");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));

        JComboBox<String> addressSelector = new JComboBox<>(addresses.toArray(String[]::new));
        addressSelector.addActionListener(event -> refreshQr((String) addressSelector.getSelectedItem()));

        JPanel topPanel = new JPanel(new BorderLayout(8, 8));
        topPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        topPanel.add(new JLabel("Local HTTPS address:"), BorderLayout.WEST);
        topPanel.add(addressSelector, BorderLayout.CENTER);

        urlLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        urlLabel.setPreferredSize(new Dimension(480, 36));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(qrLabel, BorderLayout.CENTER);
        centerPanel.add(urlLabel, BorderLayout.SOUTH);

        JButton openDownloadsButton = new JButton("Open Downloads Folder");
        openDownloadsButton.addActionListener(event -> openDownloadsFolder(frame));

        JButton stopButton = new JButton("Stop Server");
        stopButton.addActionListener(event -> {
            int choice = JOptionPane.showConfirmDialog(
                    frame,
                    "Stop the HTTPS server?",
                    "Stop Slipstream",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice == JOptionPane.YES_OPTION) {
                stopServer.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttons.add(openDownloadsButton);
        buttons.add(stopButton);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(centerPanel, BorderLayout.CENTER);
        frame.add(buttons, BorderLayout.SOUTH);
        refreshQr(addresses.getFirst());
        frame.pack();
        frame.setMinimumSize(new Dimension(520, 460));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void refreshQr(String url) {
        if (url == null) {
            return;
        }

        try {
            BufferedImage image = QrPopupViewer.createQrImage(url, 320, 320);
            qrLabel.setIcon(new ImageIcon(image));
            urlLabel.setText("<html><div style='text-align:center;width:460px'>" + url + "</div></html>");
        } catch (Exception exception) {
            qrLabel.setIcon(null);
            qrLabel.setText("Could not generate QR code");
            urlLabel.setText(exception.getMessage());
        }
    }

    private void openDownloadsFolder(JFrame parent) {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(parent, "Opening folders is not supported on this system.", "Unavailable", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(downloadDirectory.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            JOptionPane.showMessageDialog(parent, "Could not open the downloads folder: " + exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
