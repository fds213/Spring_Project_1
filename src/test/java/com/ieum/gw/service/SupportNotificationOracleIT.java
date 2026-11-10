package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.ieum.gw.dao.NotificationDAO;
import com.ieum.gw.dao.SupportDAO;
import com.ieum.gw.dto.NotificationDTO;
import com.ieum.gw.dto.SupportInquiryDTO;
import com.ieum.gw.dto.SupportInquiryFormDTO;
import com.ieum.gw.dto.SupportReplyFormDTO;
import com.ieum.gw.mapper.NotificationMapper;
import com.ieum.gw.mapper.SupportMapper;

/**
 * 이음 문의 답변 알림 SQL을 실제 Oracle에서 돌린다(NOTIFICATION.SUPPORT_INQUIRY_ID·FK_69_08·CK_69_01, 11-09_01 패치 구조).
 * 팀 DB가 아니라 이 PC에 임시로 띄운 빈 DB(db/ 스크립트로 만든 것)에만 붙는다:
 * IEUM_LOCAL_ORACLE_URL(localhost 주소만)·USER·PASSWORD 를 줬을 때만 실행하고, 자료를 실제로 커밋한다(쓰고 버리는 DB).
 */
@EnabledIfEnvironmentVariable(named = "IEUM_LOCAL_ORACLE_URL", matches = "jdbc:oracle:thin:@//localhost:.*")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SupportNotificationOracleIT {

    private JdbcTemplate jdbc;
    private SupportService support;
    private NotificationService notification;
    private long companyId;  // ieum0001
    private long member;     // 아무개(20260003) 사원 ID
    private long memberAccount;
    private long adminAccount; // 강아지(20260001): 같은 업체의 다른 계정
    private long operatorAccount; // ieum0000 운영자

    /** 임시 DB에 붙고, 서비스에 실제 트랜잭션을 씌운다 */
    @BeforeAll
    void connect() throws Exception {
        DataSource ds = new DriverManagerDataSource(System.getenv("IEUM_LOCAL_ORACLE_URL"),
                System.getenv("IEUM_LOCAL_ORACLE_USER"), System.getenv("IEUM_LOCAL_ORACLE_PASSWORD"));
        jdbc = new JdbcTemplate(ds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfigLocation(new ClassPathResource("config/mybatis-config.xml"));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        notification = new NotificationService(new NotificationDAO(session.getMapper(NotificationMapper.class)));
        ProxyFactory proxy = new ProxyFactory(new SupportService(new SupportDAO(session.getMapper(SupportMapper.class)), notification));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        support = (SupportService) proxy.getProxy();

        companyId = jdbc.queryForObject("SELECT company_id FROM company WHERE company_code = 'ieum0001'", Long.class);
        member = jdbc.queryForObject("SELECT employee_id FROM employee WHERE company_id = ? AND employee_no = '20260003'", Long.class, companyId);
        memberAccount = account(member);
        adminAccount = account(jdbc.queryForObject("SELECT employee_id FROM employee WHERE company_id = ? AND employee_no = '20260001'",
                Long.class, companyId));
        operatorAccount = jdbc.queryForObject("SELECT a.user_account_id FROM user_account a JOIN company c ON c.company_id = a.company_id "
                + "WHERE c.company_code = 'ieum0000' AND a.role_code = 'ROLE_OPS' FETCH FIRST 1 ROWS ONLY", Long.class);
    }

    /** ieum0001 사원의 계정 번호 */
    private long account(long employeeId) {
        return jdbc.queryForObject("SELECT user_account_id FROM user_account WHERE company_id = ? AND employee_id = ?",
                Long.class, companyId, employeeId);
    }

    /** 아무개 이름으로 문의를 하나 보내고 접수번호를 돌려준다 */
    private Long newInquiry(String title) {
        SupportInquiryFormDTO form = new SupportInquiryFormDTO();
        form.setTitle(title);
        form.setBody("내용");
        return support.createInquiry(companyId, member, form);
    }

    /** 답변 입력 */
    private static SupportReplyFormDTO reply(String body) {
        SupportReplyFormDTO form = new SupportReplyFormDTO();
        form.setBody(body);
        return form;
    }

    /** 아무개의 '내가 보낸 문의'에서 그 문의 한 건 */
    private SupportInquiryDTO mine(Long no) {
        return support.listMine(companyId, member, memberAccount).stream().filter(q -> q.getNo().equals(no)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("운영자가 답변하면 문의한 직원에게 알림이 저장되고, 펼치면 그 문의의 알림만 읽음이 된다")
    void replyNotifiesAndReads() {
        Long no = newInquiry("결재함 오류");
        Long other = newInquiry("근태 오류");
        int before = notification.countUnread(companyId, memberAccount);

        support.reply(no, reply("확인 중입니다"), operatorAccount);
        support.reply(no, reply("해결했습니다"), operatorAccount);
        support.reply(other, reply("확인 중입니다"), operatorAccount);
        Long replyId = mine(no).getReplies().get(0).getId();
        support.editReply(no, replyId, reply("고친 답변")); // 고쳐도 알림은 늘지 않는다

        assertEquals(before + 3, notification.countUnread(companyId, memberAccount));
        assertEquals(2, mine(no).getUnread());
        assertEquals(1, mine(other).getUnread());
        NotificationDTO latest = notification.list(companyId, memberAccount, 1).getItems().get(0);
        assertEquals("NT_SUPPORT_REPLY", latest.getNotificationType());
        assertEquals("이음 문의 답변", latest.getTitle());
        assertEquals(other, latest.getSupportInquiryId());
        assertNull(latest.getApprovalDocumentId());

        assertEquals(0, support.markRepliesRead(companyId, adminAccount, no)); // 같은 업체의 다른 계정은 못 바꾼다
        assertEquals(2, mine(no).getUnread());

        assertEquals(2, support.markRepliesRead(companyId, memberAccount, no));
        assertEquals(0, support.markRepliesRead(companyId, memberAccount, no));
        assertEquals(0, mine(no).getUnread());
        assertEquals(1, mine(other).getUnread());
        assertEquals(before + 1, notification.countUnread(companyId, memberAccount));
    }

    @Test
    @DisplayName("알림이 문의한 업체 밖의 문의를 가리키면 FK_69_08이 막는다")
    void otherCompanyNotificationRejected() {
        Long no = newInquiry("다른 업체 문의");
        long otherCompany = jdbc.queryForObject("SELECT company_id FROM company WHERE company_code = 'ieum0002'", Long.class);
        long otherEmployee = jdbc.queryForObject("SELECT employee_id FROM employee WHERE company_id = ? AND employee_no = '20260001'",
                Long.class, otherCompany);

        assertThrows(DataIntegrityViolationException.class,
                () -> notification.notifySupportReply(otherCompany, otherEmployee, no, 1L, "다른 업체 문의"));
    }

    @Test
    @DisplayName("알림 저장이 실패하면 답변과 상태 변경도 같이 취소된다")
    void notificationFailureRollsBackReply() throws Exception {
        Long no = newInquiry("알림 실패");
        NotificationService failing = new NotificationService(null) {
            @Override
            public void notifySupportReply(Long c, Long e, Long i, Long r, String t) {
                throw new DataIntegrityViolationException("ORA-02290");
            }
        };
        DataSource ds = jdbc.getDataSource();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfigLocation(new ClassPathResource("config/mybatis-config.xml"));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        ProxyFactory proxy = new ProxyFactory(new SupportService(new SupportDAO(session.getMapper(SupportMapper.class)), failing));
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        SupportService broken = (SupportService) proxy.getProxy();

        assertThrows(DataIntegrityViolationException.class, () -> broken.reply(no, reply("답변"), operatorAccount));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM support_reply WHERE support_inquiry_id = ?", Integer.class, no));
        List<String> states = jdbc.queryForList("SELECT status_code FROM support_inquiry WHERE support_inquiry_id = ?", String.class, no);
        assertEquals(List.of("SUP_RECEIVED"), states);
    }
}
