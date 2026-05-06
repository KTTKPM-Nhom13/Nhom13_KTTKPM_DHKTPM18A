package iuh.fit.email_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SendEmailRequest {
    @NotBlank
    @Email
    @Size(max = 150)
    String to;

    @NotBlank
    @Size(max = 200)
    String subject;

    String htmlContent;
    String textContent;
}
