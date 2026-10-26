package com.telusko.service;

import com.telusko.dto.Student;
import com.telusko.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private StudentRepository repository;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JWTService jwtService;

    public Map<String, Object> register(Student student){

        Map<String, Object> register = new HashMap<>();

        student.setPassword(passwordEncoder.encode(student.getPassword()));
        Student saved = repository.save(student);

        String token = jwtService.generateToken(student.getName());

        register.put("Student", saved);
        register.put("token", token);

        return register;
    }

    public List<Student> getAllStudents() {
        return repository.findAll();
    }

    public String login(Student student) {

        Authentication authentication = authenticationManager
                .authenticate(new UsernamePasswordAuthenticationToken(student.getName(), student.getPassword()));

        if (authentication.isAuthenticated())
            return jwtService.generateToken(student.getName());
        else
            return "Login Failed";
    }
}
