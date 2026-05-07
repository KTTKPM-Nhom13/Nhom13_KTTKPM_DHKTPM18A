package iuh.fit.userservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RequestAccountDeletionRequest {
    @Size(max = 255)
    String reason;
}
