package com.springboot.MyTodoList.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all unmatched frontend routes to index.html so React Router handles them.
 * Without this, direct navigation to /login, /dashboard, etc. returns 404/403.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {
        "/login",
        "/register",
        "/forgot-password",
        "/dashboard",
        "/projects",
        "/board",
        "/teams",
        "/profile",
        "/settings",
        "/analytics"
    })
    public String spa() {
        return "forward:/index.html";
    }
}
