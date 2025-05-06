/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.service;

import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

public class SystemTrayManager {
    private static TrayIcon trayIcon;
    private static SystemTray tray;
    // Windows 11风格的系统托盘图标 (使用Base64编码以避免文件依赖)
    private static final String ICON_BASE64 = 
        "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";

    private static Image createWindowsIcon() {
        try {
            // 尝试从Base64解码图像
            byte[] decodedBytes = Base64.getDecoder().decode(ICON_BASE64);
            ByteArrayInputStream bis = new ByteArrayInputStream(decodedBytes);
            BufferedImage iconImage = ImageIO.read(bis);
            return iconImage;
        } catch (IOException e) {
            // 如果解码失败，创建一个简单的图标
            return createFallbackIcon();
        }
    }
    
    private static Image createFallbackIcon() {
        BufferedImage trayIconImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = trayIconImage.createGraphics();
        
        // 绘制一个简单的蓝色方块作为图标
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 120, 212)); // Windows 11蓝色
        g.fillRoundRect(0, 0, 15, 15, 6, 6); // 圆角矩形
        g.setColor(Color.WHITE); 
        g.fillRect(4, 7, 8, 2); // 横线表示截图动作
        g.dispose();
        
        return trayIconImage;
    }

    public static void setupSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("当前平台不支持系统托盘");
            return;
        }

        try {
            tray = SystemTray.getSystemTray();
            
            // 创建弹出菜单
            PopupMenu popup = new PopupMenu();
            
            // 创建Windows 11风格的菜单项
            MenuItem takeScreenshotItem = new MenuItem("截取屏幕");
            takeScreenshotItem.addActionListener(e -> {
                // 使用与F1相同的逻辑初始化截图
                startScreenshotViaSystemTray();
            });
            
            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> {
                Platform.exit();
                System.exit(0);
            });
            
            popup.add(takeScreenshotItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            // 创建托盘图标
            Image iconImage = createWindowsIcon();
            trayIcon = new TrayIcon(iconImage, "截图贴图工具");
            trayIcon.setImageAutoSize(true);
            trayIcon.setPopupMenu(popup);
            
            // 确保图标尺寸正确
            int trayIconWidth = tray.getTrayIconSize().width;
            if (trayIconWidth > 16) {
                // 如果系统托盘需要更大的图标，调整图像大小
                BufferedImage resizedImage = new BufferedImage(trayIconWidth, trayIconWidth, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = resizedImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(iconImage, 0, 0, trayIconWidth, trayIconWidth, null);
                g.dispose();
                trayIcon.setImage(resizedImage);
            }
            
            // 添加图标到系统托盘
            tray.add(trayIcon);
            
            // 添加工具提示和双击动作
            trayIcon.setToolTip("截图贴图工具 (按F1截图)");
            trayIcon.addActionListener(e -> {
                // 双击图标时截图
                startScreenshotViaSystemTray();
            });
            
            // 右键点击行为（Windows上常见）
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        // 右键点击 - 在某些平台上需要手动显示弹出菜单
                        // PopupMenu没有setLocation方法，所以我们在这里不做特殊处理
                        // 系统默认会在右键点击位置显示菜单
                    } else if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                        // 双击 - 触发截图
                        startScreenshotViaSystemTray();
                    }
                }
            });
            
            System.out.println("系统托盘图标已设置（按F1截图）");
            
        } catch (Exception e) {
            System.err.println("设置系统托盘时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 通过系统托盘初始化截图流程，确保与F1热键使用相同的逻辑
     */
    private static void startScreenshotViaSystemTray() {
        try {
            System.out.println("[系统托盘] 开始截图流程");
            
            // 使用与F1热键相同的逻辑取消任何存在的截图
            GlobalKeyListener keyListener = GlobalKeyListener.getInstance();
            if (keyListener != null) {
                // 如果有截图正在进行，先尝试取消
                keyListener.cancelCurrentScreenshot();
                
                // 通过GlobalKeyListener触发截图，以保持一致的状态管理
                Platform.runLater(() -> {
                    try {
                        // 模拟F1键的行为，复用GlobalKeyListener中的逻辑
                        keyListener.startScreenshotFromSystemTray();
                    } catch (Exception ex) {
                        System.err.println("[系统托盘] 启动截图时出错: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                });
            } else {
                System.err.println("[系统托盘] 无法获取GlobalKeyListener实例");
                // 回退到直接创建PaneImplement的方式
                Platform.runLater(() -> {
                    PaneImplement pa = PaneImplement.build();
                    pa.snapshot(null);
                });
            }
        } catch (Exception e) {
            System.err.println("[系统托盘] 启动截图时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 出错时回退到直接方式
            Platform.runLater(() -> {
                PaneImplement pa = PaneImplement.build();
                pa.snapshot(null);
            });
        }
    }
    
    public static void removeTrayIcon() {
        if (tray != null && trayIcon != null) {
            tray.remove(trayIcon);
        }
    }
} 