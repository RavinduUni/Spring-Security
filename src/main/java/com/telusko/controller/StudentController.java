package com.telusko.controller;

import com.telusko.dto.Student;
import com.telusko.repository.StudentRepository;
import com.telusko.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class StudentController {

    @Autowired
    private StudentService studentService;

    @GetMapping("students")
    public List<Student> getStudents(){
        return studentService.getAllStudents();
    }

    @PostMapping("/create")
    public Map<String, Object> register(@RequestBody Student student){
        return studentService.register(student);
    }

    @PostMapping("/login")
    public String login(@RequestBody Student student){
        return studentService.login(student);
    }
}
