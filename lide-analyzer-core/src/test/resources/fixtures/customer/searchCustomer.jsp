<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
    <title>Customer Search</title>
</head>
<body>
<form id="searchForm" action="/customerSearch.do" method="post">
    <label for="customerId">Customer ID</label>
    <input type="text" id="customerId" name="customerId" maxlength="10" required="true" placeholder="Enter ID" />

    <label for="status">Status</label>
    <select id="status" name="status">
        <option value="">Any</option>
        <option value="ACTIVE" selected>Active</option>
        <option value="INACTIVE">Inactive</option>
    </select>

    <label for="age">Age</label>
    <input type="number" id="age" name="age" min="18" max="120" />

    <button type="submit">Search</button>
</form>

<table id="customerTable">
    <thead>
    <tr><th>ID</th><th>Name</th><th>Status</th></tr>
    </thead>
    <tbody>
    <c:forEach items="${customers}" var="customer">
        <tr>
            <td>${customer.id}</td>
            <td>${customer.name}</td>
            <td>${customer.status}</td>
        </tr>
    </c:forEach>
    </tbody>
</table>

<div class="summary">Total customers: ${summary.total}</div>
</body>
</html>
