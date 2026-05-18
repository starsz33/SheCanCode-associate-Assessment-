package technicalassessment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import technicalassessment.entity.PaymentRecord;

import java.util.Optional;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    // Find payment by idempotency key
    Optional<PaymentRecord> findByIdempotencyKey(String idempotencyKey);
}

