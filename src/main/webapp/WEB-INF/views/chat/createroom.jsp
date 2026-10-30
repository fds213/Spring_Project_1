<%@ page language="java" contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%-- 임시 화면: 생성 결과 알림 후 목록으로 이동. 모델 속성: success (boolean) --%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>채팅방 생성 결과</title>
</head>
<body>
<script>
    alert('${success ? "채팅방이 생성되었습니다." : "채팅방 생성에 실패했습니다."}');
    // replace: 뒤로가기·새로고침으로 생성 요청이 다시 전송되지 않게 한다
    location.replace('${pageContext.request.contextPath}/chatlist.do');
</script>
</body>
</html>
