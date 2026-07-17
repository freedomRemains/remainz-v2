package com.freedom.remainz_v2.web.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RemainzV2Controller {

    @GetMapping("/remainz-v2/service/top.html")
    public String getTop(Model model) {
        Map<String, String> html = new HashMap<>();
        html.put("label", "TOP画面です。");
        model.addAttribute("html", html);
        return "10000_contents";
    }
}
