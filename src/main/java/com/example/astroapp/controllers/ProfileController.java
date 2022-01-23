package com.example.astroapp.controllers;

import com.example.astroapp.dao.UserRepo;
import com.example.astroapp.entities.User;
import com.example.astroapp.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired
    UserRepo userRepo;

    @Autowired
    UserService userService;

    @GetMapping("/")
    public String displayProfile(@RequestParam String id,
                                 @RequestParam(defaultValue = "false") Boolean editable, Model model) {
        User user = userRepo.findByUserID(id);
        model.addAttribute("user", user);
        return editable ? "editableProfile" : "profile";
    }

    @PostMapping("/save")
    public String saveProfile(@RequestParam String description, @RequestParam String username) {
        User user = userService.getCurrentUser();
        user.setDescription(description);
        if (userRepo.existsByUsernameEquals(username)) {
            userRepo.save(user);
            return "redirect:/profile/?id="+user.getGoogleSub()+"&editable=true";
        }
        user.setUsername(username);
        userRepo.save(user);
        return "redirect:/profile/?id="+user.getGoogleSub();
    }

    @GetMapping("/username")
    public String displayUsernameForm() {
        return "usernameForm";
    }

    @PostMapping("/username/save")
    public String saveUsername(@RequestParam String username, RedirectAttributes redirectAttributes) {
        if (userRepo.existsByUsernameEquals(username)) {
            redirectAttributes.addFlashAttribute("message", "Username already taken");
            return "redirect:/profile/username";
        }
        User user = userService.getCurrentUser();
        user.setUsername(username);
        userRepo.save(user);
        return "redirect:/profile/?id="+user.getGoogleSub();
    }

}
