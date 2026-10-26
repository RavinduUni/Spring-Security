package com.telusko.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String greet(){
        // In a stateless JWT setup we must not call request.getSession()
        // as that creates a server-side HTTP session, defeating the STATELESS policy.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return "Hello, " + auth.getName() + "!";
    }
}
