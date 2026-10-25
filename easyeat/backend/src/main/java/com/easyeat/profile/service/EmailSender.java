package com.easyeat.profile.service;

/**
 * Domain interface for transactional email dispatch related to user profiles.
 */
public interface EmailSender {

    /**
     * Dispatches an asynchronous email change verification link to the target address.
     *
     * @param toEmail target email address
     * @param verificationUrl fully qualified URL containing the raw verification token
     */
    void sendVerificationEmail(String toEmail, String verificationUrl);
}
