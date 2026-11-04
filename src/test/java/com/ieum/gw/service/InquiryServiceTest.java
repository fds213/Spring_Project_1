package com.ieum.gw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ieum.gw.dao.InquiryDAO;
import com.ieum.gw.dto.InquiryResultDTO;
import com.ieum.gw.dto.InquirySubmitDTO;
import com.ieum.gw.dto.PublicInquiryDTO;

class InquiryServiceTest {

    private static InquirySubmitDTO valid() {
        InquirySubmitDTO f = new InquirySubmitDTO();
        f.setCompanyName("가나상사");
        f.setName("김가나");
        f.setEmail("kim@gana.com");
        f.setPhone("010-1234-5678");
        f.setTitle("도입 비용 문의");
        f.setContent("50명 기준 비용이 궁금합니다.");
        return f;
    }

    private static String repeat(int length) {
        return "가".repeat(length);
    }

    private static InquiryFormException rejected(InquirySubmitDTO f) {
        return assertThrows(InquiryFormException.class, () -> InquiryService.normalize(f));
    }

    /** 받은 행을 적어 두고 번호를 차례로 붙이는 가짜 저장소 */
    static class FakeDAO extends InquiryDAO {
        final List<PublicInquiryDTO> saved = new ArrayList<>();

        FakeDAO() {
            super(null);
        }

        @Override
        public int insert(PublicInquiryDTO dto) {
            dto.setPublicInquiryId(1000L + saved.size());
            saved.add(dto);
            return 1;
        }
    }

    @Test
    @DisplayName("입력 정리: 앞뒤 공백을 빼고 이메일은 소문자로, 내용 안의 줄바꿈은 그대로 둔다")
    void normalizes() {
        InquirySubmitDTO f = valid();
        f.setCompanyName("  가나상사 ");
        f.setName(" 김가나 ");
        f.setEmail("  Kim@Gana.COM ");
        f.setPhone(" 010-1234-5678 ");
        f.setTitle(" 도입 비용 문의 ");
        f.setContent("\n첫 줄\n둘째 줄\n");

        PublicInquiryDTO in = InquiryService.normalize(f);

        assertEquals("가나상사", in.getCompanyName());
        assertEquals("김가나", in.getRequesterName());
        assertEquals("kim@gana.com", in.getEmail());
        assertEquals("010-1234-5678", in.getPhone());
        assertEquals("도입 비용 문의", in.getTitle());
        assertEquals("첫 줄\n둘째 줄", in.getContent());
    }

    @Test
    @DisplayName("휴대전화는 선택: 없거나 공백뿐이면 null로 저장한다")
    void phoneOptional() {
        InquirySubmitDTO f = valid();
        f.setPhone(null);
        assertNull(InquiryService.normalize(f).getPhone());
        f.setPhone("   ");
        assertNull(InquiryService.normalize(f).getPhone());
    }

    @Test
    @DisplayName("필수 칸이 없거나 공백뿐이면 그 칸 이름으로 거절한다")
    void requiredFields() {
        InquirySubmitDTO noCompany = valid();
        noCompany.setCompanyName("  ");
        assertEquals("companyName", rejected(noCompany).getField());

        InquirySubmitDTO noName = valid();
        noName.setName(null);
        assertEquals("name", rejected(noName).getField());

        InquirySubmitDTO noEmail = valid();
        noEmail.setEmail("");
        assertEquals("email", rejected(noEmail).getField());

        InquirySubmitDTO noTitle = valid();
        noTitle.setTitle(null);
        assertEquals("title", rejected(noTitle).getField());

        InquirySubmitDTO noContent = valid();
        noContent.setContent(" \n ");
        assertEquals("content", rejected(noContent).getField());

        assertEquals("content", rejected(null).getField());
    }

    @Test
    @DisplayName("길이: 화면 maxlength까지 받고 한 글자 넘으면 그 칸 이름으로 거절한다")
    void lengthLimits() {
        InquirySubmitDTO max = valid();
        max.setCompanyName(repeat(InquiryService.MAX_COMPANY_NAME));
        max.setName(repeat(InquiryService.MAX_NAME));
        max.setTitle(repeat(InquiryService.MAX_TITLE));
        max.setContent(repeat(InquiryService.MAX_CONTENT));
        max.setEmail("a".repeat(InquiryService.MAX_EMAIL - "@gana.com".length()) + "@gana.com");
        InquiryService.normalize(max);

        InquirySubmitDTO company = valid();
        company.setCompanyName(repeat(InquiryService.MAX_COMPANY_NAME + 1));
        assertEquals("companyName", rejected(company).getField());

        InquirySubmitDTO name = valid();
        name.setName(repeat(InquiryService.MAX_NAME + 1));
        assertEquals("name", rejected(name).getField());

        InquirySubmitDTO title = valid();
        title.setTitle(repeat(InquiryService.MAX_TITLE + 1));
        assertEquals("title", rejected(title).getField());

        InquirySubmitDTO content = valid();
        content.setContent(repeat(InquiryService.MAX_CONTENT + 1));
        assertEquals("content", rejected(content).getField());

        InquirySubmitDTO email = valid();
        email.setEmail("a".repeat(InquiryService.MAX_EMAIL) + "@gana.com");
        assertEquals("email", rejected(email).getField());
    }

    @Test
    @DisplayName("이메일·휴대전화 형식이 틀리면 그 칸으로 거절한다. 줄바꿈이 낀 이메일도 받지 않는다")
    void formats() {
        for (String bad : new String[] { "kim", "kim@gana", "김@gana.com", "kim@gana.com\nbcc:x@y.com" }) {
            InquirySubmitDTO f = valid();
            f.setEmail(bad);
            assertEquals("email", rejected(f).getField(), bad);
        }
        for (String bad : new String[] { "01012345678", "010-12-5678", "02-1234-5678", "010-1234-567８" }) {
            InquirySubmitDTO f = valid();
            f.setPhone(bad);
            assertEquals("phone", rejected(f).getField(), bad);
        }
        for (String good : new String[] { "010-123-4567", "019-1234-5678" }) {
            InquirySubmitDTO f = valid();
            f.setPhone(good);
            assertEquals(good, InquiryService.normalize(f).getPhone());
        }
    }

    @Test
    @DisplayName("접수: 저장한 번호와 정리한 이메일을 돌려준다")
    void submit() {
        FakeDAO dao = new FakeDAO();
        InquirySubmitDTO f = valid();
        f.setEmail("Kim@Gana.com");

        InquiryResultDTO result = new InquiryService(dao).submit(f);

        assertEquals(1000L, result.getNo());
        assertEquals("kim@gana.com", result.getEmail());
        assertEquals(1, dao.saved.size());
        assertEquals("김가나", dao.saved.get(0).getRequesterName());
    }

    @Test
    @DisplayName("같은 이메일로 여러 번 접수해도 모두 저장한다")
    void sameEmailTwice() {
        FakeDAO dao = new FakeDAO();
        InquiryService service = new InquiryService(dao);

        assertEquals(1000L, service.submit(valid()).getNo());
        assertEquals(1001L, service.submit(valid()).getNo());
        assertEquals(2, dao.saved.size());
    }

    @Test
    @DisplayName("봇 막기 칸에 값이 있으면 저장하지 않고, 입력이 틀려도 성공처럼 빈 결과를 돌려준다")
    void honeypot() {
        FakeDAO dao = new FakeDAO();
        InquiryService service = new InquiryService(dao);

        InquirySubmitDTO bot = valid();
        bot.setWebsite("http://spam.example");
        InquiryResultDTO result = service.submit(bot);
        assertNull(result.getNo());
        assertNull(result.getEmail());

        InquirySubmitDTO broken = new InquirySubmitDTO();
        broken.setWebsite("x");
        assertNull(service.submit(broken).getNo());

        assertEquals(0, dao.saved.size());
    }

    @Test
    @DisplayName("입력이 틀리면 저장하지 않는다")
    void invalidNotSaved() {
        FakeDAO dao = new FakeDAO();
        InquirySubmitDTO f = valid();
        f.setEmail("kim");

        assertThrows(InquiryFormException.class, () -> new InquiryService(dao).submit(f));
        assertEquals(0, dao.saved.size());
    }
}
