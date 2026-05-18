package technicalassessment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import technicalassessment.dto.PaymentRequestDTO;
import technicalassessment.dto.PaymentResponseDTO;
import technicalassessment.entity.PaymentRecord;
import technicalassessment.repository.PaymentRecordRepository;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    @Autowired
    private PaymentRecordRepository paymentRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public ResponseEntity<PaymentResponseDTO> processPayment(
            String idempotencyKey,
            PaymentRequestDTO request) {
        try {
            String requestBody = objectMapper.writeValueAsString(request);

            Optional<PaymentRecord> existing = paymentRecordRepository
                    .findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
                PaymentRecord record = existing.get();

                // Key expired after 24hrs — Developer's Choice feature
                if (record.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                    return ResponseEntity.status(410)
                            .body(PaymentResponseDTO.builder()
                                    .message("Idempotency key has expired. Please use a new key.")
                                    .idempotencyKey(idempotencyKey)
                                    .status("EXPIRED")
                                    .build());
                }

                // Same key different body → 409 Conflict
                if (!record.getRequestBody().equals(requestBody)) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(PaymentResponseDTO.builder()
                                    .message("Idempotency key already used for a different request body.")
                                    .idempotencyKey(idempotencyKey)
                                    .status("CONFLICT")
                                    .build());
                }

                // Still processing → wait (race condition)
                if (record.isProcessing()) {
                    return waitForProcessing(idempotencyKey);
                }

                // Same key same body → return cached response
                return ResponseEntity.status(record.getStatusCode())
                        .header("X-Cache-Hit", "true")
                        .body(PaymentResponseDTO.builder()
                                .message(record.getResponseBody())
                                .idempotencyKey(idempotencyKey)
                                .status("ALREADY_PROCESSED")
                                .build());
            }

            // New request → save as processing
            PaymentRecord newRecord = PaymentRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .requestBody(requestBody)
                    .responseBody("")
                    .statusCode(201)
                    .isProcessing(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            paymentRecordRepository.save(newRecord);

            // Simulate 2 second processing delay
            Thread.sleep(2000);

            // Build response message
            String responseMessage = "Charged " + request.getAmount()
                    + " " + request.getCurrency();

            // Save final response
            newRecord.setResponseBody(responseMessage);
            newRecord.setProcessing(false);
            paymentRecordRepository.save(newRecord);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(PaymentResponseDTO.builder()
                            .message(responseMessage)
                            .idempotencyKey(idempotencyKey)
                            .status("SUCCESS")
                            .build());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PaymentResponseDTO.builder()
                            .message("Processing was interrupted.")
                            .idempotencyKey(idempotencyKey)
                            .status("ERROR")
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(PaymentResponseDTO.builder()
                            .message("Something went wrong: " + e.getMessage())
                            .idempotencyKey(idempotencyKey)
                            .status("ERROR")
                            .build());
        }
    }

    // ─────────────────────────────────────────
    // WAIT FOR PROCESSING — Race Condition Fix
    // ─────────────────────────────────────────
    private ResponseEntity<PaymentResponseDTO> waitForProcessing(String idempotencyKey) {
        int maxRetries = 10;
        int retries = 0;

        while (retries < maxRetries) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Optional<PaymentRecord> record = paymentRecordRepository
                    .findByIdempotencyKey(idempotencyKey);

            if (record.isPresent() && !record.get().isProcessing()) {
                return ResponseEntity.status(record.get().getStatusCode())
                        .header("X-Cache-Hit", "true")
                        .body(PaymentResponseDTO.builder()
                                .message(record.get().getResponseBody())
                                .idempotencyKey(idempotencyKey)
                                .status("SUCCESS")
                                .build());
            }
            retries++;
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(PaymentResponseDTO.builder()
                        .message("Payment is still processing. Please try again shortly.")
                        .idempotencyKey(idempotencyKey)
                        .status("PROCESSING")
                        .build());
    }
}
