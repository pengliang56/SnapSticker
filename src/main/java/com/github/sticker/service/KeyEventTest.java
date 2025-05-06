/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.service;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.concurrent.CountDownLatch;

/**
 * 用于测试全局热键捕获的独立程序
 * 可以单独运行此类以确认热键监听器正常工作
 */
public class KeyEventTest {
    public static void main(String[] args) {
        try {
            System.out.println("开始测试全局热键捕获...");
            System.out.println("请按下F1键以确认捕获正常工作");
            System.out.println("按ESC键结束测试");
            
            // 禁用本机钩子日志记录
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(java.util.logging.Level.OFF);
            logger.setUseParentHandlers(false);
            
            // 注册全局热键监听器
            GlobalScreen.registerNativeHook();
            
            // 创建等待锁，让程序保持运行
            CountDownLatch latch = new CountDownLatch(1);
            
            // 添加监听器
            GlobalScreen.addNativeKeyListener(new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    System.out.println("键盘按下: " + e.getKeyCode() + " (" + 
                                       NativeKeyEvent.getKeyText(e.getKeyCode()) + ")");
                    
                    if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
                        System.out.println("成功捕获F1按键！测试通过！");
                    } else if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
                        System.out.println("ESC键被按下，测试结束...");
                        latch.countDown(); // 释放锁，允许程序退出
                    }
                }
                
                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    // 不需要处理
                }
                
                @Override
                public void nativeKeyTyped(NativeKeyEvent e) {
                    // 不需要处理
                }
            });
            
            // 等待用户按下ESC键
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 清理资源
            GlobalScreen.unregisterNativeHook();
            System.out.println("测试已结束");
            
        } catch (NativeHookException e) {
            System.err.println("无法初始化全局热键监听器: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 