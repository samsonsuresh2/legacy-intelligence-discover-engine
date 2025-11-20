<html>
<head>
<script>
  function goHome() {
    window.location='home.jsp';
  }
  function goSubmit() {
    document.forms[0].action='submitForm.jsp';
  }
</script>
</head>
<body>
<form id="testForm" action="noop.jsp"></form>
</body>
</html>
