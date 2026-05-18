package technicalassessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import technicalassessment.dto.PaymentRequestDTO;
import technicalassessment.entity.PaymentRecord;
import technicalassessment.repository.PaymentRecordRepository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final PaymentRecordRepository paymentRecordRepository;


    public Map<String, Object> processPayment(String idempotencyKey, PaymentRequestDTO paymentRequestDTO) throws InterruptedException {
        Optional<PaymentRecord> existing = paymentRecordRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            PaymentRecord record = existing.get();

            // Same key, different body — reject it
            if (!record.getRequestBody().equals(paymentRequestDTO.getAmount() + paymentRequestDTO.getCurrency())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Idempotency key already used for a different request body.");
                return error;
            }

            // Same key, same body — return cached response
            Map<String, Object> cached = new HashMap<>();
            cached.put("status", "success");
            cached.put("message", record.getResponseBody());
            cached.put("cached", true);
            return cached;
        }

        // First time — process the payment
        Thread.sleep(2000); // simulate processing delay

        // Save the record
        PaymentRecord record = new PaymentRecord();
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestBody(paymentRequestDTO.getAmount() + paymentRequestDTO.getCurrency());
        record.setResponseBody("Charged " + paymentRequestDTO.getAmount() + " " + paymentRequestDTO.getCurrency());
        record.setStatusCode(201);
        record.setProcessing(false);
        record.setCreatedAt(LocalDateTime.now());
        paymentRecordRepository.save(record);

        // Return response
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Charged " + paymentRequestDTO.getAmount() + " " + paymentRequestDTO.getCurrency());
        return response;
    }
}
