package technicalassessment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import technicalassessment.dto.PaymentRequestDTO;
import technicalassessment.dto.PaymentResponseDTO;
import technicalassessment.service.IdempotencyService;
@RestController
@RequestMapping("/api")
public class PaymentController {
    @Autowired
    private IdempotencyService idempotencyService;

    @PostMapping("/process-payment")
    public ResponseEntity<PaymentResponseDTO> processPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDTO request) {

        return idempotencyService.processPayment(idempotencyKey, request);
    }
}
