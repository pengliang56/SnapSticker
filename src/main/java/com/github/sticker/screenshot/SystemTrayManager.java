/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.screenshot;

import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Manages the system tray functionality for the application.
 * Handles tray icon creation, menu items, and system tray interactions.
 */
public class SystemTrayManager {
    private final ScreenshotSelector screenshotSelector;
    private TrayIcon trayIcon;
    private FileLock lock;
    private FileChannel channel;
    private static final String LOCK_FILE = System.getProperty("user.home") + "/.snapsticker.lock";
    private boolean isShuttingDown = false;
    private SystemTray tray;

    // Icon paths
    private static final String ICON_PATH = "/tray_icon.png";  // 16x16 PNG
    private static final String ICON_PATH_2X = "/tray_icon@2x.png";  // 32x32 PNG for high DPI

    // Font settings
    private static final String MENU_FONT_NAME = "Segoe UI";  // Modern Windows font
    private static final int MENU_FONT_SIZE = 15;  // Increased size for better readability
    private static final int MENU_FONT_STYLE = Font.PLAIN;  // Clean and modern look

    // Menu item text with status indicator placeholder and keyboard shortcuts
    private static final String STATUS_PLACEHOLDER = "  ";  // Two spaces for status indicator
    private static final String SHORTCUT_PLACEHOLDER = "                                              ";  // Space for shortcut alignment
    private static final String SHORTCUT_PLACEHOLDER2_STRING = "                                 ";  // Space for shortcut alignment
    private static final String CLEAR_RECORDS = STATUS_PLACEHOLDER + "Clear snip records";
    private static final String PASTE = STATUS_PLACEHOLDER + "Paste" + SHORTCUT_PLACEHOLDER + "F3";
    private static final String SNIP = STATUS_PLACEHOLDER + "Snip";
    private static final String SNIP_AND_COPY = STATUS_PLACEHOLDER + "Snip and copy" + SHORTCUT_PLACEHOLDER2_STRING + "F1";
    private static final String HIDE_SHOW = STATUS_PLACEHOLDER + "Hide/Show all images";
    private static final String HELP = STATUS_PLACEHOLDER + "Help";
    private static final String PREFERENCES = STATUS_PLACEHOLDER + "Preferences...";
    private static final String MOTTO = STATUS_PLACEHOLDER + "Simplicity is the ultimate sophistication";
    private static final String RESTART = STATUS_PLACEHOLDER + "Restart (Ctrl+R)";
    private static final String QUIT = STATUS_PLACEHOLDER + "Quit (Alt+F4)";

    /**
     * Constructor for SystemTrayManager
     */
    public SystemTrayManager(ScreenshotSelector screenshotSelector) {
        this.screenshotSelector = screenshotSelector;
        tray = SystemTray.getSystemTray();
    }

    /**
     * Check if another instance is running and handle it
     *
     * @return true if this is the only instance, false if another instance exists
     */
    private boolean checkAndHandleExistingInstance() {
        try {
            // Create lock file if it doesn't exist
            File lockFile = new File(LOCK_FILE);
            if (!lockFile.exists()) {
                lockFile.createNewFile();
            }

            // Try to acquire lock
            channel = new RandomAccessFile(lockFile, "rw").getChannel();
            lock = channel.tryLock();

            if (lock == null) {
                // Another instance is running
                System.out.println("Another instance is running. Attempting to close it...");

                // Read the PID from the lock file
                String pid = new String(Files.readAllBytes(Paths.get(LOCK_FILE))).trim();
                if (!pid.isEmpty()) {
                    try {
                        // Try to kill the existing process
                        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                            Runtime.getRuntime().exec("taskkill /F /PID " + pid);
                        } else {
                            Runtime.getRuntime().exec("kill -9 " + pid);
                        }
                        // Wait a bit for the process to terminate
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        System.err.println("Failed to terminate existing instance: " + e.getMessage());
                    }
                }

                // Try to acquire lock again
                lock = channel.tryLock();
                if (lock == null) {
                    System.err.println("Could not acquire lock after attempting to close existing instance");
                    return false;
                }
            }

            // Write our PID to the lock file
            String pid = String.valueOf(ProcessHandle.current().pid());
            channel.truncate(0);
            channel.write(java.nio.ByteBuffer.wrap(pid.getBytes()));

            return true;
        } catch (Exception e) {
            System.err.println("Error checking for existing instance: " + e.getMessage());
            return false;
        }
    }

    /**
     * Release the lock file
     */
    private void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
            // Delete the lock file
            Files.deleteIfExists(Paths.get(LOCK_FILE));
        } catch (Exception e) {
            System.err.println("Error releasing lock: " + e.getMessage());
        }
    }

    /**
     * Initialize the system tray with icon and menu items
     */
    public void initialize() {
        Platform.setImplicitExit(false);
        // Check for existing instance first
        if (!checkAndHandleExistingInstance()) {
            System.err.println("Could not start application: Another instance is running");
            Platform.exit();
            return;
        }

        if (!SystemTray.isSupported()) {
            System.err.println("System tray is not supported");
            Platform.exit();
            return;
        }

        try {
            // Load the tray icon
            BufferedImage image = loadTrayIcon();
            if (image == null) {
                throw new RuntimeException("Could not load tray icon");
            }

            // Create AWT popup menu
            PopupMenu popup = new PopupMenu();

            // Create font for menu items
            Font menuFont = new Font(MENU_FONT_NAME, MENU_FONT_STYLE, MENU_FONT_SIZE);

            // Add menu items
            MenuItem clearRecords = new MenuItem(CLEAR_RECORDS);
            MenuItem paste = new MenuItem(PASTE);
            MenuItem snip = new MenuItem(SNIP);
            MenuItem snipAndCopy = new MenuItem(SNIP_AND_COPY);
            MenuItem hideShow = new MenuItem(HIDE_SHOW);
            MenuItem help = new MenuItem(HELP);
            MenuItem preferences = new MenuItem(PREFERENCES);
            MenuItem motto = new MenuItem(MOTTO);
            MenuItem restart = new MenuItem(RESTART);
            MenuItem quit = new MenuItem(QUIT);

            // Apply font to all menu items
            for (MenuItem item : new MenuItem[]{clearRecords, paste, snip, snipAndCopy,
                    hideShow, help, preferences, restart, quit}) {
                item.setFont(menuFont);
            }

            // Set motto style
            motto.setFont(new Font(MENU_FONT_NAME, Font.ITALIC, MENU_FONT_SIZE));  // Set italic style
            motto.setEnabled(false);  // Make it non-clickable

            // Set specific items to disabled state
            clearRecords.setEnabled(false);  // Disable Clear snip records
            hideShow.setEnabled(false);      // Disable Hide/Show all images
            snip.setEnabled(false);          // Disable Snip
            help.setEnabled(false);          // Disable Help
            preferences.setEnabled(false);    // Disable Preferences
            restart.setEnabled(false);       // Disable Restart

            // Add action listeners
            quit.addActionListener(e -> Platform.runLater(() -> {
                Platform.exit();
                tray.remove(trayIcon);
            }));
            snipAndCopy.addActionListener(e -> Platform.runLater(this::takeScreenshot));

            // Add items to popup menu
            popup.add(clearRecords);
            popup.add(paste);
            popup.add(snip);
            popup.add(snipAndCopy);
            popup.add(hideShow);
            popup.addSeparator();
            popup.add(help);
            popup.add(preferences);
            popup.add(motto);
            popup.addSeparator();
            popup.add(restart);
            popup.add(quit);

            // Create tray icon
            trayIcon = new TrayIcon(image, "Screenshot Sticker", popup);
            trayIcon.setImageAutoSize(true);

            // Add to system tray
            tray.add(trayIcon);
        } catch (Exception e) {
            System.err.println("Failed to initialize system tray: " + e.getMessage());
            e.printStackTrace();
            Platform.exit();
        }
    }

    /**
     * Load the appropriate tray icon based on system DPI
     *
     * @return The loaded tray icon image
     */
    private BufferedImage loadTrayIcon() {
        try {
            // Check if we're on a high DPI display
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            double scale = gc.getDefaultTransform().getScaleX();

            // Try to load high DPI icon first if we're on a high DPI display
            if (scale > 1.0) {
                InputStream iconStream = getClass().getResourceAsStream(ICON_PATH_2X);
                if (iconStream != null) {
                    return ImageIO.read(iconStream);
                }
            }

            // Fall back to standard icon
            InputStream iconStream = getClass().getResourceAsStream(ICON_PATH);
            if (iconStream != null) {
                return ImageIO.read(iconStream);
            }

            System.err.println("Could not find any icon resources");
            return null;

        } catch (Exception e) {
            System.err.println("Failed to load tray icon: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Handle screenshot action
     * This will be implemented to capture the screen
     */
    private void takeScreenshot() {
        screenshotSelector.startSelection();
    }

    /**
     * Clean up system tray resources
     */
    public void cleanup() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
        releaseLock();
    }

    /**
     * Exit the application
     * This method ensures proper cleanup of resources before exit
     */
    private void exitApplication() {
        if (isShuttingDown) {
            return; // Prevent multiple exit calls
        }
        isShuttingDown = true;

        try {
            cleanup();
            Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error during application shutdown: " + e.getMessage());
            e.printStackTrace();
            // Force exit if cleanup fails
            System.exit(1);
        }
    }
} 