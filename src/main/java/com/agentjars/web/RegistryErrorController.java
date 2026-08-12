package com.agentjars.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Renders errors in the site's own layout instead of the container's default page. */
@Controller
public class RegistryErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handle(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        int code = status instanceof Integer value ? value : 500;

        model.addAttribute("status", code);
        model.addAttribute("title", switch (code) {
            case 404 -> "Not found";
            case 400 -> "Bad request";
            case 403 -> "Forbidden";
            default -> "Something went wrong";
        });
        model.addAttribute("detail", message == null || message.toString().isBlank()
                ? "The page you asked for is not part of this registry."
                : message.toString());
        model.addAttribute("activeNav", "");
        return "error";
    }
}
