package com.ieum.gw.service;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 메일 설정. 값은 저장소에 올리지 않는 config/mail.properties에 둔다(mail.properties.example 참고).
 * 파일이 없으면 메일을 끈 것으로 보고 서버는 그대로 뜬다. 메일을 끄면 인증 링크를 받을 길이 없으므로 이메일 인증도 요구하지 않는다.
 */
@Component
@PropertySource(value = "classpath:config/mail.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class MailSettings implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MailSettings.class);
    private static final String TIMEOUT_MILLIS = "5000"; // 메일 서버가 답하지 않아도 요청이 끝나게. 연결·읽기·쓰기 단계마다 적용된다

    @Value("${mail.enabled:false}")
    private boolean enabled;
    @Value("${mail.require-verification:true}")
    private boolean requireVerification;
    @Value("${mail.host:smtp.gmail.com}")
    private String host;
    @Value("${mail.port:587}")
    private int port;
    @Value("${mail.username:}")
    private String username;
    @Value("${mail.password:}")
    private String password;
    @Value("${mail.from-name:이음}")
    private String fromName;
    @Value("${mail.base-url:http://localhost:8080}")
    private String baseUrl;
    // 로컬 가짜 메일 서버로 시험할 때만 false. Gmail은 STARTTLS 없이는 로그인을 받지 않는다
    @Value("${mail.starttls:true}")
    private boolean starttls;

    @Override
    public void afterPropertiesSet() {
        if (enabled && username.isEmpty()) {
            logger.warn("mail.enabled=true인데 mail.username이 비어 있어 메일을 끈 것으로 봅니다.");
        }
        if (!isEnabled()) {
            logger.warn("메일 발송이 꺼져 있습니다(config/mail.properties). 메일을 보내지 않고 이메일 인증도 요구하지 않습니다.");
        }
    }

    /** 계정을 비운 채 켜 두면 모든 신청이 메일 실패로 로그인까지 막히므로 끈 것으로 본다 */
    public boolean isEnabled() {
        return enabled && !username.isEmpty();
    }

    /** 이메일 인증 전 업체의 로그인·직원 가입을 막는가(ACC-04·06) */
    public boolean isVerificationRequired() {
        return isEnabled() && requireVerification;
    }

    /** 보내는 주소는 Gmail 계정 주소로 고정된다 */
    public String getFrom() {
        return username;
    }

    public String getFromName() {
        return fromName;
    }

    /** 메일 속 링크의 앞부분. 요청의 Host 값으로 만들면 남의 주소가 링크에 들어갈 수 있어 설정에서 읽는다 */
    public String getBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public JavaMailSender newSender() {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username.isEmpty() ? null : username);
        sender.setPassword(password.isEmpty() ? null : password);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.setProperty("mail.smtp.auth", String.valueOf(!username.isEmpty()));
        props.setProperty("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.setProperty("mail.smtp.starttls.required", String.valueOf(starttls));
        props.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        // 인증서가 정말 그 메일 서버의 것인지 본다. 끄면 강의실·발표장 와이파이에서 앱 비밀번호를 가로챌 수 있다
        props.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        props.setProperty("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
        props.setProperty("mail.smtp.timeout", TIMEOUT_MILLIS);
        props.setProperty("mail.smtp.writetimeout", TIMEOUT_MILLIS);
        return sender;
    }
}
