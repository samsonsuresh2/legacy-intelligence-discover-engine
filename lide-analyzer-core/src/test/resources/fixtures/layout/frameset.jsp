<%@ page language="java" %>
<html>
<head><title>Legacy Layout</title></head>
<frameset cols="25%,75%" name="rootSet">
    <frame name="menu" src="/legacy/menu.jsp" />
    <frameset rows="50%,50%" name="contentSet">
        <frame name="top" src="/legacy/top.jsp" />
        <frame id="bottom" src="details.jsp" />
    </frameset>
</frameset>
</html>
