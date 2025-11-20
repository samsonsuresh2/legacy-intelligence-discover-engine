package com.example.legacy.forms;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.apache.struts.action.ActionForm;

public class CustomerSearchForm extends ActionForm {

    @NotNull
    @Size(max = 10)
    private String customerId;

    @Pattern(regexp = "[A-Z]+")
    private String status;

    @Min(18)
    @Max(120)
    private Integer age;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }
}
