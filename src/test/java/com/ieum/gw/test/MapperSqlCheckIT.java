package com.ieum.gw.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.ExpressionEvaluator;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * CI의 db-check 전용: Mapper의 SQL을 실제 Oracle에서 EXPLAIN PLAN으로 검사해 없는 표·칼럼과 문법 오류를 찾는다.
 * 이름이 IT로 끝나 mvn verify에서는 돌지 않는다. -Dtest로 직접 불러도 환경 변수 IEUM_DB_CHECK_URL이 없으면 건너뛴다.
 * 실행하지 않고 해석만 하므로 업체 조건이 빠진 것 같은 로직 문제는 잡지 못한다.
 */
@EnabledIfEnvironmentVariable(named = "IEUM_DB_CHECK_URL", matches = ".+")
class MapperSqlCheckIT {

    /** 어떤 조건 조합에서 나와도 그 SQL 자체의 문제인 오류: 없는 칼럼, 없는 표, 없는 시퀀스, 모호한 칼럼 */
    private static final Set<Integer> NAME_ERRORS = Set.of(904, 942, 2289, 918);

    /** EXPLAIN PLAN이 받지 않는 문장: PL/SQL 블록, 프로시저 호출, 구조 변경 */
    private static final Pattern NOT_EXPLAINABLE = Pattern.compile("\\s*(?:\\{|(?:BEGIN|DECLARE|CALL|ALTER|DROP|TRUNCATE)\\b)", Pattern.CASE_INSENSITIVE);

    /** <bind>가 계산할 파라미터. 어떤 이름을 물어도 문자열을 돌려줘 title.trim() 같은 식이 돈다. */
    private static final Map<String, Object> ANY_VALUE = new HashMap<>() {
        @Override
        public Object get(Object key) {
            return "1";
        }

        @Override
        public boolean containsKey(Object key) {
            return true;
        }
    };

    /** 문자열·따옴표 이름·주석을 먼저 맞춰, 그 안의 ?는 바꾸지 않는다 */
    private static final Pattern PLACEHOLDER = Pattern.compile("'[^']*'|\"[^\"]*\"|--[^\n]*|/\\*.*?\\*/|\\?", Pattern.DOTALL);

    @Test
    @DisplayName("모든 Mapper 문장이 DB의 표·칼럼과 맞고 문법 오류가 없다")
    void explainAllStatements() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(System.getenv("IEUM_DB_CHECK_URL"),
                System.getenv("IEUM_DB_CHECK_USER"), System.getenv("IEUM_DB_CHECK_PASSWORD"));
        Configuration configuration = loadConfiguration(dataSource);

        List<String> failed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int total = 0;
        int explained = 0;
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.setEscapeProcessing(false);
            for (String id : new TreeSet<>(configuration.getMappedStatementNames())) {
                if (!id.contains(".")) {
                    continue; // 같은 문장이 짧은 이름으로도 등록돼 있다
                }
                total++;
                MappedStatement mapped = configuration.getMappedStatement(id);
                List<Rendered> variants;
                try {
                    variants = render(mapped.getSqlSource());
                } catch (Exception e) {
                    skipped.add(id + ": SQL을 만들지 못했습니다: " + firstLine(e));
                    continue;
                }
                for (Rendered variant : variants) {
                    if (NOT_EXPLAINABLE.matcher(variant.sql).lookingAt()) {
                        skipped.add(id + ": EXPLAIN PLAN으로 볼 수 없는 문장입니다.\n    " + oneLine(variant.sql));
                        break;
                    }
                    explained++;
                    SQLException error = explain(statement, variant.sql);
                    if (error == null) {
                        continue;
                    }
                    String line = id + variant.label + ": " + firstLine(error) + "\n    " + oneLine(fillWithNull(variant.sql));
                    // 조건을 모두 끄거나 둘 이상 켠 조합의 문법 오류는 실제로 나오지 않는 조합일 수 있다(예: 빈 <set>, ORDER BY 두 번)
                    if (variant.combination && !NAME_ERRORS.contains(error.getErrorCode())) {
                        skipped.add(line);
                    } else {
                        failed.add(line);
                    }
                }
            }
            connection.rollback();
        }

        report(total, explained, failed, skipped);
        assertTrue(total > 0, "읽은 Mapper 문장이 없습니다. mapper 경로를 확인하세요.");
        assertTrue(failed.isEmpty(), "SQL 오류 " + failed.size() + "건:\n" + String.join("\n", failed));
    }

    /** root-context.xml의 sessionFactory와 같은 설정으로 읽는다 */
    private static Configuration loadConfiguration(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfigLocation(new ClassPathResource("config/mybatis-config.xml"));
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/**/*.xml"));
        return factory.getObject().getConfiguration();
    }

    /** 동적 SQL의 조건을 켜고 끄며 나올 수 있는 SQL을 모은다. <foreach>는 항목 두 개로 돌아 구분자 누락도 드러난다. */
    private static List<Rendered> render(SqlSource source) throws Exception {
        if (!(source instanceof DynamicSqlSource)) {
            return List.of(new Rendered(source.getBoundSql(ANY_VALUE).getSql(), "", false));
        }
        SqlNode root = rootNode((DynamicSqlSource) source);
        Branches branches = new Branches();
        installEvaluator(root, branches);

        Map<String, Rendered> variants = new LinkedHashMap<>();
        branches.allOn();
        add(variants, source, branches, " [조건 모두 켬]");
        branches.allOff();
        add(variants, source, branches, " [조건 모두 끔]");
        // <choose>의 뒤쪽 조건은 앞 조건을 꺼야 보이므로, 새로 보이는 조건이 없을 때까지 돈다
        Set<String> done = new HashSet<>();
        while (done.size() < branches.seen.size()) {
            for (String test : new ArrayList<>(branches.seen)) {
                if (done.add(test)) {
                    branches.onlyOn(test);
                    add(variants, source, branches, " [" + test + "만 켬]");
                    branches.onlyOff(test);
                    add(variants, source, branches, " [" + test + "만 끔]");
                }
            }
        }
        return new ArrayList<>(variants.values());
    }

    private static void add(Map<String, Rendered> variants, SqlSource source, Branches branches, String label) {
        branches.conditionsOn = 0;
        String sql = source.getBoundSql(ANY_VALUE).getSql();
        boolean combination = !branches.seen.isEmpty() && branches.conditionsOn != 1;
        variants.putIfAbsent(sql, new Rendered(sql, label, combination));
    }

    private static SqlNode rootNode(DynamicSqlSource source) throws Exception {
        Field field = DynamicSqlSource.class.getDeclaredField("rootSqlNode");
        field.setAccessible(true);
        return (SqlNode) field.get(source);
    }

    /** MyBatis가 공개 API로 열어 주지 않아, 동적 SQL 노드의 조건 평가기를 리플렉션으로 바꾼다 */
    private static void installEvaluator(Object node, ExpressionEvaluator evaluator) throws Exception {
        for (Class<?> type = node.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(node);
                if (value instanceof ExpressionEvaluator) {
                    field.set(node, evaluator);
                } else if (value instanceof SqlNode) {
                    installEvaluator(value, evaluator);
                } else if (value instanceof List) {
                    for (Object child : (List<?>) value) {
                        if (child instanceof SqlNode) {
                            installEvaluator(child, evaluator);
                        }
                    }
                }
            }
        }
    }

    /** #{...}가 바뀐 ?를 NULL로 채운다 */
    private static String fillWithNull(String sql) {
        return PLACEHOLDER.matcher(sql).replaceAll(m -> Matcher.quoteReplacement(m.group().equals("?") ? "NULL" : m.group()));
    }

    /**
     * ?를 값을 넣지 않은 바인드 변수로 바꾼다. EXPLAIN PLAN은 값이 없어도 해석한다.
     * 괄호가 없으면 구분자를 빠뜨린 "? ?"가 ":v :v"(표시 변수 문법)로 해석돼 통과한다.
     */
    private static String fillWithBind(String sql) {
        return PLACEHOLDER.matcher(sql).replaceAll(m -> Matcher.quoteReplacement(m.group().equals("?") ? "(:v)" : m.group()));
    }

    /**
     * 값을 모르는 자리는 NULL로 해석해 본다. NULL을 받지 않는 식(NULLIF(?, ''), ? + INTERVAL)이 있어
     * 이름 오류가 아니면 바인드 변수로 한 번 더 해 보고, 그래도 안 될 때만 오류로 본다.
     */
    private static SQLException explain(Statement statement, String sql) {
        SQLException first = tryExplain(statement, fillWithNull(sql));
        if (first == null || NAME_ERRORS.contains(first.getErrorCode())) {
            return first;
        }
        return tryExplain(statement, fillWithBind(sql)) == null ? null : first;
    }

    private static SQLException tryExplain(Statement statement, String sql) {
        try {
            statement.execute("EXPLAIN PLAN FOR " + sql);
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private static void report(int total, int explained, List<String> failed, List<String> skipped) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("Mapper SQL 검사: 문장 ").append(total).append("개, EXPLAIN ").append(explained)
                .append("번, 실패 ").append(failed.size()).append("건, 건너뜀 ").append(skipped.size()).append("건\n");
        appendList(out, "실패", failed);
        appendList(out, "건너뜀", skipped);
        System.out.print(out);
        if (!skipped.isEmpty()) {
            System.out.println("::warning::Mapper SQL 검사에서 " + skipped.size() + "건을 건너뛰었습니다. 로그의 [건너뜀] 줄을 확인하세요.");
        }
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null) {
            Files.write(Paths.get(summary), ("```\n" + out + "```\n").getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static void appendList(StringBuilder out, String title, List<String> lines) {
        for (String line : lines) {
            out.append('[').append(title).append("] ").append(line).append('\n');
        }
    }

    private static String firstLine(Exception e) {
        String message = String.valueOf(e.getMessage());
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }

    private static String oneLine(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }

    private static final class Rendered {
        final String sql;
        final String label;
        final boolean combination;

        Rendered(String sql, String label, boolean combination) {
            this.sql = sql;
            this.label = label;
            this.combination = combination;
        }
    }

    /** 조건식의 값을 계산하지 않고 정해 둔 대로 답한다 */
    private static final class Branches extends ExpressionEvaluator {
        final Set<String> seen = new LinkedHashSet<>();
        int conditionsOn;
        private boolean defaultAnswer;
        private String exception;

        void allOn() {
            defaultAnswer = true;
            exception = null;
        }

        void allOff() {
            defaultAnswer = false;
            exception = null;
        }

        void onlyOn(String test) {
            defaultAnswer = false;
            exception = test;
        }

        void onlyOff(String test) {
            defaultAnswer = true;
            exception = test;
        }

        @Override
        public boolean evaluateBoolean(String expression, Object parameterObject) {
            seen.add(expression);
            boolean answer = expression.equals(exception) ? !defaultAnswer : defaultAnswer;
            if (answer) {
                conditionsOn++;
            }
            return answer;
        }

        @Override
        public Iterable<?> evaluateIterable(String expression, Object parameterObject, boolean nullable) {
            return Arrays.asList(1, 2);
        }
    }
}
