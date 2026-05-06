package iuh.fit.email_service.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.email_service.dto.request.SendEmailRequest;
import iuh.fit.email_service.service.BrevoEmailService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/emails")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalEmailController {
    BrevoEmailService brevoEmailService;

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody SendEmailRequest request) {
        brevoEmailService.sendEmail(request);
        return ApiResponse.<Void>builder()
                .message("Email request dispatched successfully")
                .build();
    }
}
