package com.example.legacy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.legacy.forms.CustomerSearchForm;

@Controller
@RequestMapping("/customer")
public class CustomerController {

    @PostMapping("/search")
    public String search(@ModelAttribute CustomerSearchForm form) {
        return "ok";
    }
}
