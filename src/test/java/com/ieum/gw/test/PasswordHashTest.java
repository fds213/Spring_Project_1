package com.ieum.gw.test;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashTest {

    @Test
    public void makeHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String hash1 = encoder.encode("1111");   // 원문을 해시로 바꾸는 메서드
        String hash2 = encoder.encode("2222");
        String hash3 = encoder.encode("3333");
        String hash4 = encoder.encode("4444");
        String hash5 = encoder.encode("5555");
        System.out.println(hash1);
        System.out.println(hash2);
        System.out.println(hash3);
        System.out.println(hash4);
        System.out.println(hash5);

        // 해시가 원문과 맞는지 확인하는 메서드
        System.out.println(encoder.matches("1111", hash1));
        System.out.println(encoder.matches("2222", hash2));
        System.out.println(encoder.matches("3333", hash3));
        System.out.println(encoder.matches("4444", hash4));
        System.out.println(encoder.matches("5555", hash5));
    }
}