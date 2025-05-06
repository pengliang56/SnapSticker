/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.service;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class GlobalKeyListener implements NativeKeyListener {
    // 静态单例实例，用于其他类访问
    private static GlobalKeyListener instance;
    
    // 记录上次按键时间 - 仅用于日志记录
    private long lastKeyPressTime = 0;
    // 完全移除防抖动延迟，让用户可以随时截图
    // private static final long DEBOUNCE_DELAY = 300;
    
    // F1键的键码常量
    private static final int F1_KEY_CODE = 112; // Windows下F1的键码可能是112
    // F3键的键码常量
    private static final int F3_KEY_CODE = 114; // Windows下F3的键码通常是114
    // ESC键的键码常量
    private static final int ESC_KEY_CODE = 27; // Windows下ESC的键码通常是27
    // 记录上次截图是否正在进行
    private final AtomicBoolean screenshotInProgress = new AtomicBoolean(false);
    // 记录是否已经选择了区域，等待确认
    private final AtomicBoolean areaSelectedWaitingConfirm = new AtomicBoolean(false);
    // 超时任务调度器
    private final ScheduledExecutorService timeoutScheduler = new ScheduledThreadPoolExecutor(1);
    // 当前活动的截图面板
    private PaneImplement currentPaneImplement;
    
    // 打印一条调试信息，确认监听器已注册
    public GlobalKeyListener() {
        System.out.println("=============================================");
        System.out.println("全局热键监听器已初始化");
        System.out.println("F1快捷键: 开始截图选择");
        System.out.println("F3快捷键: 确认截图");
        System.out.println("ESC快捷键: 取消截图");
        System.out.println("按F1键开始截图");
        System.out.println("=============================================");
        
        // 保存为单例实例
        instance = this;
    }
    
    /**
     * 获取全局单例实例
     */
    public static GlobalKeyListener getInstance() {
        return instance;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        try {
            // 输出按键代码以便调试
            int keyCode = e.getKeyCode();
            String keyText = NativeKeyEvent.getKeyText(keyCode);
            long currentTime = System.currentTimeMillis();
            System.out.println("[按键] 键码=" + keyCode + ", 按键=" + keyText + ", 距上次按键=" + (currentTime - lastKeyPressTime) + "ms");
            
            // 检查是否是ESC键
            boolean isEscKey = (keyCode == NativeKeyEvent.VC_ESCAPE) || 
                             (keyCode == ESC_KEY_CODE) || 
                             ("Escape".equals(keyText));
            
            // 如果是ESC键，尝试取消当前截图 - 始终最高优先级处理
            if (isEscKey) {
                System.out.println("[按键] ESC键被按下 - 紧急取消任何进行中的操作");
                // 强制取消任何进行中的操作，无论状态如何
                cancelCurrentScreenshot();
                
                // 额外保障：强制重置所有状态标志
                screenshotInProgress.set(false);
                areaSelectedWaitingConfirm.set(false);
                currentPaneImplement = null;
                
                // 触发垃圾回收，帮助释放资源
                System.gc();
                
                return;
            }
            
            // 检查是否是F3键 - 确认截图
            boolean isF3Key = (keyCode == NativeKeyEvent.VC_F3) || 
                            (keyCode == F3_KEY_CODE) || 
                            ("F3".equals(keyText));
                            
            if (isF3Key) {
                System.out.println("[按键] F3键被按下");
                
                // 即使不在等待确认状态，也尝试进行处理
                if (areaSelectedWaitingConfirm.get()) {
                    // 正常流程 - 区域已选择，等待确认
                    System.out.println("[按键] F3键被按下 - 确认截图");
                    
                    if (currentPaneImplement != null) {
                        try {
                            // 先清除状态标志，防止按键处理被阻塞
                            screenshotInProgress.set(false);
                            areaSelectedWaitingConfirm.set(false);
                            
                            // 保存当前PaneImplement的引用以便操作
                            PaneImplement paneToConfirm = currentPaneImplement;
                            // 先清空引用，避免被后续操作影响
                            currentPaneImplement = null;
                            
                            // 重要修复：使用Platform.runLater确保在JavaFX线程上调用confirmCapture
                            Platform.runLater(() -> {
                                try {
                                    paneToConfirm.confirmCapture();
                                    System.out.println("[按键] 截图已确认，并已清除窗口引用");
                                } catch (Exception ex) {
                                    System.err.println("[错误] 确认截图时JavaFX线程内发生异常: " + ex.getMessage());
                                    ex.printStackTrace();
                                }
                            });
                        } catch (Exception ex) {
                            System.err.println("[错误] 确认截图时发生异常: " + ex.getMessage());
                            ex.printStackTrace();
                            
                            // 出错时强制取消
                            cancelCurrentScreenshot();
                        }
                    } else {
                        System.out.println("[按键] 无法确认截图 - 没有活动的截图面板");
                        // 清理状态
                        screenshotInProgress.set(false);
                        areaSelectedWaitingConfirm.set(false);
                    }
                } else if (screenshotInProgress.get()) {
                    // 异常流程 - 截图正在进行但不在等待确认状态
                    System.out.println("[按键] F3键被按下 - 但截图不在等待确认状态，强制取消截图");
                    cancelCurrentScreenshot();
                } else {
                    // 没有截图状态
                    System.out.println("[按键] F3键被按下 - 但当前没有活动的截图");
                }
                
                return;
            }
            
            // 检查截图状态
            if (screenshotInProgress.get() || areaSelectedWaitingConfirm.get()) {
                System.out.println("[按键] 已有截图正在进行，忽略此次按键");
                
                // 添加安全检查：如果键盘事件到来但状态仍锁定，可能是UI线程阻塞
                // 检查上次按键时间与当前时间的差值，如果超过3秒，强制重置状态
                if (lastKeyPressTime > 0 && (currentTime - lastKeyPressTime) > 3000) {
                    System.out.println("[按键] 检测到潜在的状态锁定问题，强制重置状态（上次按键时间=" + 
                                     (currentTime - lastKeyPressTime) + "ms前）");
                    screenshotInProgress.set(false);
                    areaSelectedWaitingConfirm.set(false);
                    
                    // 如果F1被按下，继续处理
                    boolean isF1Key = (keyCode == NativeKeyEvent.VC_F1) || 
                                   (keyCode == F1_KEY_CODE) || 
                                   ("F1".equals(keyText));
                    if (!isF1Key) {
                        return; // 不是F1键，仍然忽略
                    }
                    
                    // 记录状态被重置
                    System.out.println("[按键] 状态已重置，继续处理F1按键");
                } else {
                    return; // 正常忽略
                }
            }
            
            // 检查是否是F1键 (尝试多种可能的键码)
            boolean isF1Key = (keyCode == NativeKeyEvent.VC_F1) || 
                            (keyCode == F1_KEY_CODE) || 
                            ("F1".equals(keyText));
            
            if (isF1Key) {
                // 标记为开始处理截图
                if (!screenshotInProgress.compareAndSet(false, true)) {
                    System.out.println("[按键] 已有截图正在进行，忽略此次按键");
                    return;
                }
                
                System.out.println("=======================================");
                System.out.println("[按键] F1键被按下 - 开始截图流程");
                lastKeyPressTime = currentTime;
                
                // 在开始新的截图前，确保任何存在的选择窗口被关闭
                if (currentPaneImplement != null) {
                    System.out.println("[按键] F1按下前发现存在的选择窗口，尝试关闭");
                    try {
                        // 保存引用，避免重入问题
                        PaneImplement oldPane = currentPaneImplement;
                        currentPaneImplement = null;
                        
                        // 取消旧窗口，确保它被关闭
                        Platform.runLater(() -> {
                            try {
                                oldPane.cancelCapture();
                                System.out.println("[按键] 成功关闭旧的选择窗口");
                            } catch (Exception ex) {
                                System.err.println("[错误] 关闭旧选择窗口时出错: " + ex.getMessage());
                            }
                        });
                        
                        // 给UI线程一点时间去处理关闭
                        Thread.sleep(50);
                    } catch (Exception ex) {
                        System.err.println("[错误] 尝试关闭旧选择窗口时出错: " + ex.getMessage());
                    }
                }
                
                // 实际启动截图操作
                initiateScreenshotProcess();
            }
        } catch (Exception ex) {
            // 捕获并记录任何可能的异常
            System.err.println("[错误] 处理按键时发生严重错误: " + ex.getMessage());
            ex.printStackTrace();
            
            // 确保重置状态标志
            screenshotInProgress.set(false);
            areaSelectedWaitingConfirm.set(false);
            currentPaneImplement = null;
        }
    }
    
    /**
     * 从F1热键或系统托盘启动截图流程
     * 抽取为独立方法以便复用逻辑
     */
    private void initiateScreenshotProcess() {
        try {
            // 清理任何旧的超时定时器
            cleanupTimeoutScheduler();
            
            // 设置超时保护，防止用户选择区域后忘记确认
            scheduleScreenshotTimeout();
            
            // 创建并显示屏幕截图选择界面
            Platform.runLater(() -> {
                try {
                    System.out.println("[截图] 正在创建截图界面");
                    
                    // 创建新的截图面板
                PaneImplement pa = PaneImplement.build();
                    
                    // 保存引用以便后续操作
                    currentPaneImplement = pa;
                    
                    // 显示截图选择界面
                pa.snapshot(null);
                    
                    System.out.println("[截图] 已显示截图选择界面");
                } catch (Exception e) {
                    System.err.println("[错误] 创建截图界面时出错: " + e.getMessage());
                    e.printStackTrace();
                    
                    // 确保重置状态
                    screenshotInProgress.set(false);
                    areaSelectedWaitingConfirm.set(false);
                    currentPaneImplement = null;
                }
            });
        } catch (Exception ex) {
            System.err.println("[错误] 初始化截图流程时出错: " + ex.getMessage());
            ex.printStackTrace();
            
            // 确保重置状态
            screenshotInProgress.set(false);
            areaSelectedWaitingConfirm.set(false);
            currentPaneImplement = null;
        }
    }
    
    /**
     * 支持从系统托盘启动截图功能
     * 这个方法会被SystemTrayManager调用
     */
    public void startScreenshotFromSystemTray() {
        try {
            System.out.println("[系统托盘] 通过GlobalKeyListener开始截图流程");
            
            // 检查是否已有截图正在进行
            if (screenshotInProgress.get() || areaSelectedWaitingConfirm.get()) {
                System.out.println("[系统托盘] 已有截图正在进行，取消当前截图并开始新的");
                
                // 取消当前截图
                cancelCurrentScreenshot();
                
                // 确保状态标志被重置
                screenshotInProgress.set(false);
                areaSelectedWaitingConfirm.set(false);
            }
            
            // 标记为开始处理截图
            if (!screenshotInProgress.compareAndSet(false, true)) {
                System.out.println("[系统托盘] 无法设置截图状态，可能有其他截图正在进行");
                return;
            }
            
            System.out.println("[系统托盘] 开始新的截图流程");
            lastKeyPressTime = System.currentTimeMillis();
            
            // 使用与F1相同的逻辑启动截图流程
            initiateScreenshotProcess();
        } catch (Exception e) {
            System.err.println("[系统托盘] 启动截图时出错: " + e.getMessage());
            e.printStackTrace();
            
            // 确保重置状态
            screenshotInProgress.set(false);
            areaSelectedWaitingConfirm.set(false);
            currentPaneImplement = null;
        }
    }
    
    /**
     * 清理超时调度器中的任务
     */
    private void cleanupTimeoutScheduler() {
        try {
            // 尝试清理所有待定任务
            timeoutScheduler.shutdownNow();
            
            // 重新创建定时器
            ScheduledExecutorService newScheduler = new ScheduledThreadPoolExecutor(1);
            
            // 使用反射设置字段
            java.lang.reflect.Field field = GlobalKeyListener.class.getDeclaredField("timeoutScheduler");
            field.setAccessible(true);
            field.set(this, newScheduler);
            
            System.out.println("[截图] 已清理和重置超时调度器");
        } catch (Exception e) {
            System.err.println("[错误] 清理超时调度器时出错: " + e.getMessage());
            // 忽略错误并继续，这不是关键操作
        }
    }
    
    /**
     * 取消当前活动的截图
     * 该方法可以被系统托盘功能调用
     */
    public void cancelCurrentScreenshot() {
        try {
            System.out.println("[截图] 正在取消当前截图操作...");
            
            // 如果存在活动的截图面板，尝试取消它
            if (currentPaneImplement != null) {
                try {
                    // 保存引用以便操作，避免并发修改问题
                    PaneImplement paneToCancel = currentPaneImplement;
                    
                    // 立即清空引用，避免反复尝试取消
                    currentPaneImplement = null;
                    
                    // 在JavaFX线程上执行取消操作
                    Platform.runLater(() -> {
                        try {
                            // 调用取消方法
                            paneToCancel.cancelCapture();
                            System.out.println("[截图] 成功取消截图");
                        } catch (Exception ex) {
                            System.err.println("[错误] 取消截图时JavaFX线程内发生异常: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    });
                } catch (Exception ex) {
                    System.err.println("[错误] 取消截图时发生异常: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                System.out.println("[截图] 没有活动的截图面板需要取消");
            }
            
            // 强制重置所有状态标志
            screenshotInProgress.set(false);
            areaSelectedWaitingConfirm.set(false);
            currentPaneImplement = null;
            
            // 清理任何待定的超时任务
            cleanupTimeoutScheduler();
            
            System.out.println("[截图] 截图操作已完全取消");
        } catch (Exception ex) {
            System.err.println("[错误] 取消截图时发生严重错误: " + ex.getMessage());
            ex.printStackTrace();
            
            // 确保状态被重置
            screenshotInProgress.set(false);
            areaSelectedWaitingConfirm.set(false);
            currentPaneImplement = null;
        }
    }
    
    private void scheduleScreenshotTimeout() {
        try {
            // 将超时时间从3秒延长到30秒，给用户充足的调整时间
            timeoutScheduler.schedule(() -> {
                try {
                    if (screenshotInProgress.get() || areaSelectedWaitingConfirm.get()) {
                        System.out.println("[超时] 截图操作超时，强制重置状态");
                        
                        // 强制重置标志
                        screenshotInProgress.set(false);
                        areaSelectedWaitingConfirm.set(false);
                        
                        // 尝试取消当前截图
                        cancelCurrentScreenshot();
                    }
                } catch (Exception e) {
                    System.err.println("[错误] 超时处理中出错: " + e.getMessage());
                    e.printStackTrace();
                    
                    // 确保重置标志
                    screenshotInProgress.set(false);
                    areaSelectedWaitingConfirm.set(false);
                    currentPaneImplement = null;
                }
            }, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[错误] 设置超时任务时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 设置截图已完成的通知，由PaneImplement调用
     */
    public void notifyScreenshotCompleted() {
        // 重置正在截图标志，但保留区域已选择标志
        screenshotInProgress.set(false);
        
        // 设置已选择区域，等待确认标志
        areaSelectedWaitingConfirm.set(true);
        
        System.out.println("[截图] 收到区域选择完成通知，等待F3确认");
    }
    
    /**
     * 设置截图已完全完成的通知，由PaneImplement在截图确认后调用
     */
    public void notifyScreenshotFullyCompleted() {
        // 立即重置所有状态，允许下次截图
        screenshotInProgress.set(false);
        areaSelectedWaitingConfirm.set(false);
        currentPaneImplement = null;
        
        // 为了加快响应速度，也重置上次按键时间
        lastKeyPressTime = 0;
        
        System.out.println("[截图] 收到截图完全完成通知，立即允许下次截图");
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // 可以留空，我们只关心按键按下事件
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 可以留空，我们只关心按键按下事件
    }
}
