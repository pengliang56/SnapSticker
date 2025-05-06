/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.util;

/**
 * 单例锁功能测试
 * 可以通过运行此类测试SingleInstanceLock的功能
 */
public class SingleInstanceTest {
    
    public static void main(String[] args) {
        System.out.println("正在测试单例锁功能...");
        
        // 尝试获取锁
        boolean lockAcquired = SingleInstanceLock.tryLock();
        
        if (lockAcquired) {
            System.out.println("成功获取锁！这是第一个实例。");
            System.out.println("请在5秒内再次运行此测试程序...");
            
            // 等待用户运行第二个实例
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 释放锁，模拟程序退出
            System.out.println("现在释放锁，模拟程序退出...");
            SingleInstanceLock.releaseLock();
            
            System.out.println("锁已释放。您现在可以再次运行此测试，应该作为第一个实例运行。");
        } else {
            System.out.println("无法获取锁！已经有一个实例在运行。");
            
            // 尝试再次获取锁（用于测试）
            System.out.println("等待5秒后再次尝试获取锁...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            boolean secondAttempt = SingleInstanceLock.tryLock();
            if (secondAttempt) {
                System.out.println("第二次尝试获取锁成功！原实例可能已退出。");
                SingleInstanceLock.releaseLock();
            } else {
                System.out.println("第二次尝试获取锁仍然失败！原实例仍在运行。");
            }
        }
    }
} 