/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.util;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 单例锁工具类，确保应用程序在系统中只有一个实例在运行
 */
public class SingleInstanceLock {
    private static final String LOCK_FILE_NAME = ".snapsticker.lock";
    private static FileChannel channel;
    private static FileLock lock;
    
    /**
     * 尝试获取应用程序锁，如果已经有实例在运行则返回false
     * @return true表示成功获取锁（首个实例），false表示锁已被占用（已有实例在运行）
     */
    public static boolean tryLock() {
        try {
            // 在用户临时目录创建锁文件
            Path lockFile = Paths.get(System.getProperty("java.io.tmpdir"), LOCK_FILE_NAME);
            
            // 确保目录存在
            Files.createDirectories(lockFile.getParent());
            
            // 获取文件通道
            channel = new RandomAccessFile(lockFile.toFile(), "rw").getChannel();
            
            // 尝试获取独占锁，不阻塞
            lock = channel.tryLock();
            
            // 如果锁为null，说明已被其他进程锁定
            if (lock == null) {
                System.out.println("应用程序已经在运行中...");
                closeChannel();
                return false;
            }
            
            // 添加JVM关闭钩子，确保清理锁资源
            Runtime.getRuntime().addShutdownHook(new Thread(SingleInstanceLock::releaseLock));
            
            return true;
        } catch (Exception e) {
            System.err.println("检查应用程序实例时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 出错时释放资源
            releaseLock();
            return false;
        }
    }
    
    /**
     * 尝试获取锁，如果失败则显示对话框并退出
     * @param exitOnFailure 如果为true，则在锁定失败时自动退出
     * @return true表示成功获取锁，false表示失败
     */
    public static boolean tryLockWithDialog(boolean exitOnFailure) {
        boolean lockAcquired = tryLock();
        
        if (!lockAcquired) {
            // 在EDT线程上显示消息对话框
            SwingUtilities.invokeLater(() -> {
                try {
                    // 设置对话框UI样式为系统样式
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    
                    // 显示提示消息
                    JOptionPane.showMessageDialog(
                        null,
                        "截图贴图工具已经在运行中，请查看系统托盘图标。\n" +
                        "如果找不到图标，可能需要重启系统。",
                        "程序已运行",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    System.err.println("显示对话框时出错: " + e.getMessage());
                }
                
                // 如果需要，自动退出
                if (exitOnFailure) {
                    System.exit(0);
                }
            });
        }
        
        return lockAcquired;
    }
    
    /**
     * 释放锁和关联资源
     */
    public static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
                lock = null;
            }
            closeChannel();
        } catch (Exception e) {
            System.err.println("释放锁资源时出错: " + e.getMessage());
        }
    }
    
    /**
     * 关闭文件通道
     */
    private static void closeChannel() {
        try {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        } catch (Exception e) {
            System.err.println("关闭文件通道时出错: " + e.getMessage());
        }
    }
} 