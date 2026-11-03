package com.ieum.gw.attendance.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ieum.gw.attendance.dao.AttendanceDAO;
import com.ieum.gw.attendance.dto.HolidayDTO;

import lombok.RequiredArgsConstructor;

/**
 * 휴일 (업체마다, holiday). 휴가 날짜 계산(주말·휴일 빼기)과 일정 달력이 쓴다.
 * 근무 > 휴일 관리(업체 관리자·인사 담당)에서 하루씩 넣고 지우거나, 그 해 법정 공휴일을 한 번에 넣는다.
 * 휴일 자료가 없는 해에는 휴가 날짜를 계산하지 않는다(LeaveService.calculateLeaveDays).
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

    private static final int NAME_MAX = 50;
    private static final int YEAR_MIN = 2000; // 휴일 관리 화면에서 볼 수 있는 해. 넣을 때도 같은 범위만 받는다
    private static final int YEAR_MAX = 2100;

    // 법정 공휴일 (대체공휴일·선거일 포함). 정부 발표로 확인한 해만 둔다. 다른 해는 하루씩 넣는다
    private static final Map<Integer, String[][]> PRESETS = Map.of(2026, new String[][] {
        { "2026-01-01", "신정" },
        { "2026-02-16", "설날 연휴" }, { "2026-02-17", "설날" }, { "2026-02-18", "설날 연휴" },
        { "2026-03-01", "삼일절" }, { "2026-03-02", "대체공휴일(삼일절)" },
        { "2026-05-05", "어린이날" },
        { "2026-05-24", "부처님오신날" }, { "2026-05-25", "대체공휴일(부처님오신날)" },
        { "2026-06-03", "전국동시지방선거" },
        { "2026-06-06", "현충일" },
        { "2026-08-15", "광복절" }, { "2026-08-17", "대체공휴일(광복절)" },
        { "2026-09-24", "추석 연휴" }, { "2026-09-25", "추석" }, { "2026-09-26", "추석 연휴" },
        { "2026-10-03", "개천절" }, { "2026-10-05", "대체공휴일(개천절)" },
        { "2026-10-09", "한글날" },
        { "2026-12-25", "성탄절" },
    });

    private final AttendanceDAO dao;

    /** 기간 안의 휴일 (날짜순, 양 끝 포함). 연동 계약 getHolidays */
    public List<HolidayDTO> holidays(Long companyId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || toDate.isBefore(fromDate)) return List.of();
        return dao.selectHolidays(companyId, fromDate.toString(), toDate.toString());
    }

    /** 휴일 관리 화면: 그 해 휴일과, 한 번에 넣을 수 있는 해 */
    public Map<String, Object> list(Long companyId, int year) {
        if (year < YEAR_MIN || year > YEAR_MAX) throw new AttendanceException(400, "해를 다시 골라 주세요");
        List<Map<String, Object>> items = new ArrayList<>();
        for (HolidayDTO h : dao.selectHolidays(companyId, year + "-01-01", year + "-12-31")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getHolidayId());
            m.put("date", h.getHolidayDate().toString());
            m.put("weekday", h.getHolidayDate().getDayOfWeek().getValue() % 7); // 0 = 일요일
            m.put("name", h.getHolidayName());
            items.add(m);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("year", year);
        res.put("readOnly", dao.isCompanyExpired(companyId));
        res.put("presetYears", new TreeSet<>(PRESETS.keySet()));
        res.put("items", items);
        return res;
    }

    @Transactional
    public Map<String, Object> add(Long companyId, String date, String name) {
        requireWritable(companyId);
        LocalDate day = parseDay(date);
        if (day.getYear() < YEAR_MIN || day.getYear() > YEAR_MAX) {
            throw new AttendanceException(400, "휴일은 " + YEAR_MIN + "~" + YEAR_MAX + "년 안에서 넣어 주세요");
        }
        String title = name == null ? "" : name.trim();
        if (title.isEmpty() || title.length() > NAME_MAX) {
            throw new AttendanceException(400, "휴일 이름은 1~" + NAME_MAX + "자로 적어 주세요");
        }
        try {
            dao.insertHoliday(companyId, day.toString(), title);
        } catch (DuplicateKeyException e) {
            throw new AttendanceException(409, day + "은(는) 이미 휴일입니다");
        }
        return Map.of("date", day.toString(), "name", title);
    }

    @Transactional
    public Map<String, Object> delete(Long companyId, Long holidayId) {
        requireWritable(companyId);
        if (holidayId == null || dao.deleteHoliday(companyId, holidayId) == 0) {
            throw new AttendanceException(404, "이미 지워졌거나 없는 휴일입니다");
        }
        return Map.of("id", holidayId);
    }

    /** 그 해 법정 공휴일을 한 번에. 이미 있는 날은 건너뛴다 */
    @Transactional
    public Map<String, Object> addPreset(Long companyId, Integer year) {
        requireWritable(companyId);
        String[][] preset = year == null ? null : PRESETS.get(year);
        if (preset == null) throw new AttendanceException(404, "그 해의 공휴일 목록이 없습니다. 하루씩 넣어 주세요");
        Set<String> have = new HashSet<>();
        for (HolidayDTO h : dao.selectHolidays(companyId, year + "-01-01", year + "-12-31")) have.add(h.getHolidayDate().toString());
        int added = 0;
        for (String[] p : preset) {
            if (have.contains(p[0])) continue;
            dao.insertHoliday(companyId, p[0], p[1]);
            added++;
        }
        return Map.of("year", year, "added", added, "skipped", preset.length - added);
    }

    private void requireWritable(Long companyId) {
        if (dao.isCompanyExpired(companyId)) throw new AttendanceException(403, "이용 기간이 끝나 읽기 전용입니다");
    }

    private static LocalDate parseDay(String date) {
        try {
            return LocalDate.parse(date == null ? "" : date.trim());
        } catch (DateTimeParseException e) {
            throw new AttendanceException(400, "날짜를 골라 주세요");
        }
    }
}
