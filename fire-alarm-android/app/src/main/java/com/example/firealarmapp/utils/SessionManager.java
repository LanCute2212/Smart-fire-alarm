package com.example.firealarmapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences("FireAlarmPrefs", Context.MODE_PRIVATE);
    }

    public void saveAuthToken(String token, String username, String role) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("TOKEN", token);
        editor.putString("USERNAME", username);
        editor.putString("ROLE", role);
        editor.apply();
    }

    public String getToken() {
        return prefs.getString("TOKEN", "");
    }
    
    public String getRole() {
        return prefs.getString("ROLE", "USER");
    }
    
    public void clear() {
        prefs.edit().clear().apply();
    }
}
