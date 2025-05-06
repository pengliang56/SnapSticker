/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.sticker.service.GlobalKeyListener;
import com.github.sticker.service.SystemTrayManager;
import com.github.sticker.util.SingleInstanceLock;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScreenshotStickerApp extends Application {
    // 应用程序实例
    private static ScreenshotStickerApp instance;
    // 系统托盘支持标志
    private boolean systemTraySupported = false;
    // 隐藏主窗口
    private Stage hiddenStage;
    // 全局热键监听器
    private GlobalKeyListener keyListener;
    // 应用初始化完成标志
    private static final CountDownLatch appInitialized = new CountDownLatch(1);
    // JavaFX Toolkit初始化标志
    private static boolean javafxInitialized = false;
    
    public static ScreenshotStickerApp getInstance() {
        return instance;
    }
    
    public static void main(String[] args) {
        System.out.println("Copyright (c) 2025 Luka. Licensed under MIT.");
        System.out.println("程序启动中...");
        
        // 检查是否已有实例在运行，如果有则显示消息并退出
        if (!SingleInstanceLock.tryLockWithDialog(true)) {
            // tryLockWithDialog方法会自动调用System.exit(0)
            return;
        }
        
        System.out.println("获取程序实例锁成功，继续启动...");
        
        // 设置特殊的系统属性，帮助隐藏任务栏图标
        System.setProperty("javafx.window.no-taskbar", "true");
        System.setProperty("glass.win.uiScale", "1.0");
        
        // 启动JavaFX应用
        try {
            launch(args);
        } catch (Exception e) {
            System.err.println("启动JavaFX应用程序失败: " + e.getMessage());
            e.printStackTrace();
            // 确保释放实例锁
            SingleInstanceLock.releaseLock();
            System.exit(1);
        }
    }

    @Override
    public void init() throws Exception {
        // 初始化代码，在JavaFX线程启动前运行
        System.out.println("正在初始化应用...");
        super.init();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("JavaFX应用程序启动...");
        instance = this;
        javafxInitialized = true;
        
        try {
            // 检查系统托盘支持
            systemTraySupported = SystemTray.isSupported();
            if (!systemTraySupported) {
                System.err.println("当前平台不支持系统托盘，应用程序可能无法按预期工作。");
            }
    
            // 禁用本机钩子日志记录
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);
    
            // 确保热键能够正确注册（先卸载可能存在的钩子，然后重新注册）
            initializeHotkeys();
            
            // 创建一个完全隐藏但有效的JavaFX窗口，以确保Platform.runLater正常工作
            hiddenStage = createHiddenStage();
            
            // 设置系统托盘图标
            Platform.runLater(() -> {
                try {
                    // 确保系统托盘设置在JavaFX线程完全初始化后运行
                    SystemTrayManager.setupSystemTray();
                    
                    // 打印启动确认消息
                    System.out.println("==============================================");
                    System.out.println("  截图贴图工具已成功启动 - 按F1键可以截图");
                    System.out.println("  系统托盘中的图标可用于退出程序");
                    System.out.println("==============================================");
                    
                    // 通知其他等待的线程应用已初始化
                    appInitialized.countDown();
                } catch (Exception e) {
                    System.err.println("设置系统托盘时发生错误: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            // 防止当所有窗口关闭时自动退出
            Platform.setImplicitExit(false);
        } catch (Exception e) {
            System.err.println("启动应用时出错: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * 创建隐藏但有效的JavaFX窗口
     */
    private Stage createHiddenStage() {
        System.out.println("创建隐藏窗口以支持JavaFX线程...");
        // 创建一个最小的内容面板
        BorderPane contentPane = new BorderPane();
        Scene scene = new Scene(contentPane, 1, 1);
        
        // 创建完全透明和隐藏的窗口
        Stage stage = new Stage(StageStyle.UTILITY);
        stage.setScene(scene);
        stage.setWidth(1);
        stage.setHeight(1);
        stage.setOpacity(0.0);
        stage.setX(-32000);
        stage.setY(-32000);
        
        // 显示窗口（但实际上看不到）
        stage.show();
        stage.setIconified(true);
        
        System.out.println("隐藏窗口创建完成");
        return stage;
    }
    
    /**
     * 初始化全局热键监听
     */
    private void initializeHotkeys() {
        try {
            System.out.println("正在初始化全局热键监听...");
            // 先尝试卸载已有的钩子
            try {
                GlobalScreen.unregisterNativeHook();
                System.out.println("已卸载可能存在的旧全局热键钩子");
            } catch (Exception e) {
                // 忽略可能的错误，因为钩子可能尚未注册
            }
            
            // 重新注册全局热键监听器
            GlobalScreen.registerNativeHook();
            
            // 创建并注册热键监听器
            keyListener = new GlobalKeyListener();
            GlobalScreen.addNativeKeyListener(keyListener);
            
            System.out.println("全局热键已注册，F1键用于截图");
        } catch (NativeHookException e) {
            System.err.println("无法注册全局热键: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws Exception {
        System.out.println("应用程序正在关闭...");
        // 清理全局热键资源
        try {
            GlobalScreen.removeNativeKeyListener(keyListener);
            GlobalScreen.unregisterNativeHook();
            System.out.println("已卸载全局热键钩子");
        } catch (Exception e) {
            System.err.println("清理全局热键资源时出错: " + e.getMessage());
        }
        
        // 清理系统托盘图标
        SystemTrayManager.removeTrayIcon();
        
        // 清理隐藏窗口
        if (hiddenStage != null) {
            hiddenStage.close();
        }
        
        // 释放单例锁
        SingleInstanceLock.releaseLock();
        
        System.out.println("应用程序已完全关闭");
        super.stop();
    }
    
    /**
     * 确认JavaFX线程已初始化
     */
    public static boolean isJavaFXInitialized() {
        return javafxInitialized;
    }
    
    /**
     * 等待应用程序初始化完成
     */
    public static void waitForInitialization() throws InterruptedException {
        appInitialized.await();
    }
    
    /**
     * 等待应用程序初始化完成，最多等待指定的时间
     */
    public static boolean waitForInitialization(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return appInitialized.await(timeout, unit);
    }
}
