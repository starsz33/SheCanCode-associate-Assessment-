package technicalassessment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    @Column(name = "request_body", nullable = false, columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;
    @Column(name = "status_code", nullable = false)
    private int statusCode;
    @Column(name = "is_processing", nullable = false)
    private boolean Processing = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
