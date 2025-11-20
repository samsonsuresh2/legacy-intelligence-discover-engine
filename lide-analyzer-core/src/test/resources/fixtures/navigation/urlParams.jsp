<html>
<head><title>URL Param Fixture</title></head>
<body>
<a href="nextPage.jsp?mode=view&customerId=123">Next Page</a>
<script>
  window.location = 'redirect.jsp?id=999&mode=edit';
  var apiEndpoint = "data.jsp?token=${session.token}&refresh=true";
  var inlineQuery = '?simple=true&toggle=on';
</script>
</body>
</html>
