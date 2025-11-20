<%@ page language="java" %>
<html>
<head><title>Audit Trail</title></head>
<body>
<% String user = (String) session.getAttribute("user"); %>
<div class="header">Logged in as: ${sessionScope.user}</div>
<iframe src="/legacy/navFrame.jsp"></iframe>
<form id="auditFilter" action="/auditLookup.do" method="get">
    <label for="fromDate">From</label>
    <input type="date" id="fromDate" name="fromDate" />
    <label for="toDate">To</label>
    <input type="date" id="toDate" name="toDate" />
    <button type="submit">Filter</button>
</form>
<table id="auditTable">
    <c:forEach items="${auditRecords}" var="audit">
        <tr>
            <td>${audit.id}</td>
            <td>${audit.actor}</td>
            <td>${audit.action}</td>
        </tr>
    </c:forEach>
</table>
</body>
</html>
