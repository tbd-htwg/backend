package com.tripplanning.user;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    //Prüft, ob der übergebene User der aktuell am System angemeldete User ist.
        public boolean isCurrentUser(UserEntity user) {
        if (user == null || user.getId() == null) return false;

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (principal instanceof Jwt jwt) {
            // Wir vergleichen die ID aus dem Token mit der ID der Entity
            String currentUserId = jwt.getSubject(); 
            return currentUserId.equals(String.valueOf(user.getId()));
        }
        return false;
    }
}