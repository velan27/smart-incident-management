package com.smartims.service.impl;

import com.smartims.entity.OtpVerification;
import com.smartims.enums.OtpPurpose;
import com.smartims.exception.BadRequestException;
import com.smartims.service.AuditLogService;
import com.smartims.service.EmailService;
import com.smartims.service.OtpException;
import com.smartims.service.OtpService;
import com.smartims.repository.OtpVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@Transactional
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpVerificationRepository otpRepo;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    @Override
    public void generateAndSendOtp(String email, OtpPurpose purpose) {

        otpRepo.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .ifPresent(lastOtp -> {
                    if (Duration.between(lastOtp.getCreatedAt(), LocalDateTime.now()).toSeconds() < 30) {
                        throw new BadRequestException("Please wait before requesting another OTP");
                    }
                });

        String otp = String.valueOf(100000 + new Random().nextInt(900000));

        OtpVerification entity = new OtpVerification();
        entity.setEmail(email);
        entity.setOtp(otp);
        entity.setPurpose(purpose);
        entity.setVerified(false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setExpiryTime(LocalDateTime.now().plusMinutes(3));

        otpRepo.save(entity);

        try {
            emailService.sendOtpEmail(email, otp, purpose);
      
} catch (Exception ex) {
    org.slf4j.LoggerFactory.getLogger(OtpServiceImpl.class)
            .error("OTP email failed for purpose {}: {}", purpose, ex.getMessage(), ex);

    auditLogService.logSystem(
            "OTP_EMAIL_FAILED",
            "Failed to send OTP email for purpose: " + purpose
                    + ". Error: " + ex.getClass().getSimpleName(),
            null,
            "EMAIL"
    );
}


        auditLogService.logSystem(
                "OTP_GENERATED",
                "OTP generated and sent for purpose: " + purpose,
                null,
                "OTP"
        );
    }

    @Override
    public void verifyOtp(String email, String otp, OtpPurpose purpose) {

        OtpVerification record = otpRepo
                .findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new OtpException("OTP not found"));

        if (record.isVerified()) {
            throw new OtpException("OTP already used");
        }

        if (record.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new OtpException("OTP expired");
        }

        if (!record.getOtp().equals(otp)) {
            throw new OtpException("Invalid OTP");
        }

        record.setVerified(true);
        otpRepo.save(record);

        auditLogService.logSystem(
                "OTP_VERIFIED",
                "OTP verified successfully for purpose: " + purpose,
                null,
                "OTP"
        );
    }

    @Override
    public boolean isOtpVerified(String email, OtpPurpose purpose) {
        return otpRepo
                .findTopByEmailAndPurposeAndVerifiedTrueOrderByCreatedAtDesc(email, purpose)
                .isPresent();
    }
}
