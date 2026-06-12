package com.kawashreh.ecommerce.frontend.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kawashreh.ecommerce.common.dto.ErrorResponse;
import com.kawashreh.ecommerce.common.exceptions.DuplicateEntityException;
import com.kawashreh.ecommerce.common.exceptions.NoSuchElementException;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Set<String> ACTION_VERBS = Set.of("add", "delete", "remove", "create", "update", "edit");

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(FeignException.class)
    public ModelAndView handleFeign(FeignException ex, HttpServletRequest request) {
        String message = extractMessage(ex);
        return redirectWithError(request, message);
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ModelAndView handleDuplicate(DuplicateEntityException ex, HttpServletRequest request) {
        return redirectWithError(request, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ModelAndView handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        return redirectWithError(request, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ModelAndView handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return redirectWithError(request, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleAll(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return redirectWithError(request, "An unexpected error occurred");
    }

    private String extractMessage(FeignException ex) {
        if (ex.contentUTF8() == null || ex.contentUTF8().isBlank()) {
            return switch (ex.status()) {
                case 404 -> "Resource not found";
                case 409 -> "Resource already exists";
                case 400 -> "Invalid request";
                default -> "Service error";
            };
        }
        try {
            return objectMapper.readValue(ex.contentUTF8(), ErrorResponse.class).getMessage();
        } catch (Exception e) {
            return "Service error";
        }
    }

    private ModelAndView redirectWithError(HttpServletRequest request, String message) {
        String path = resolveRedirectPath(request.getRequestURI());
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8);
        return new ModelAndView("redirect:" + path + "?error=" + encoded);
    }

    private String resolveRedirectPath(String requestUri) {
        List<String> segments = Arrays.asList(requestUri.split("/"));
        if (segments.isEmpty()) return "/";
        String last = segments.get(segments.size() - 1);
        if (ACTION_VERBS.contains(last)) {
            return String.join("/", segments.subList(0, segments.size() - 1));
        }
        return requestUri;
    }
}
