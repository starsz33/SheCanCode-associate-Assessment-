package technicalassessment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
@Data
@Builder
public class PaymentRequestDTO
{
    private BigDecimal amount;
    private String currency;
}
