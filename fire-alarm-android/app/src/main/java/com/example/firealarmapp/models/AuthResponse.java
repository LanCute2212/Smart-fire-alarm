package com.example.firealarmapp.models;

public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private String role;

    public String getToken() { return token; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}
