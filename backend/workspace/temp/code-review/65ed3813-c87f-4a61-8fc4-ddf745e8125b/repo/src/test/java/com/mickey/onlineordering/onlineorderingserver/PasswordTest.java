package com.mickey.onlineordering.onlineorderingserver;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码测试类 - 用于生成和验证BCrypt密码
 */
public class PasswordTest {
    
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    @Test
    public void generatePasswords() {
        System.out.println("========================================");
        System.out.println("生成密码哈希");
        System.out.println("========================================");
        
        // 生成 admin123 的哈希
        String adminPassword = "admin123";
        String adminHash = encoder.encode(adminPassword);
        System.out.println("admin 密码: " + adminPassword);
        System.out.println("admin 哈希: " + adminHash);
        System.out.println();
        
        // 生成 user123 的哈希
        String userPassword = "user123";
        String userHash = encoder.encode(userPassword);
        System.out.println("user001 密码: " + userPassword);
        System.out.println("user001 哈希: " + userHash);
        System.out.println("========================================");
    }
    
    @Test
    public void verifyExistingHashes() {
        System.out.println("========================================");
        System.out.println("验证现有密码哈希");
        System.out.println("========================================");
        
        // 验证 admin123
        String adminPassword = "admin123";
        String adminHashFromDb = "$2a$10$jug.9nloJm5rZiq.9JvmRuA8enrqDsjnDGymPPJ.j4xNQpjqAbuai";
        boolean adminMatch = encoder.matches(adminPassword, adminHashFromDb);
        System.out.println("admin 密码验证: " + (adminMatch ? "✅ 成功" : "❌ 失败"));
        System.out.println("密码: " + adminPassword);
        System.out.println("哈希: " + adminHashFromDb);
        System.out.println();
        
        // 验证 user123
        String userPassword = "user123";
        String userHashFromDb = "$2a$10$1ZGn8xqDGWi8YeC3bXt7sOLF0/Wu6qGhpq2bZHK/TV34xv6RmsFi6";
        boolean userMatch = encoder.matches(userPassword, userHashFromDb);
        System.out.println("user001 密码验证: " + (userMatch ? "✅ 成功" : "❌ 失败"));
        System.out.println("密码: " + userPassword);
        System.out.println("哈希: " + userHashFromDb);
        System.out.println("========================================");
    }
    
    public static void main(String[] args) {
        PasswordTest test = new PasswordTest();
        test.generatePasswords();
        System.out.println();
        test.verifyExistingHashes();
    }
}




