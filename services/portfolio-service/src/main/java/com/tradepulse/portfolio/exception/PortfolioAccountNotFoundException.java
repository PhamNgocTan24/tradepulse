package com.tradepulse.portfolio.exception;

import java.util.UUID;

/**
 * Thrown when the portfolio-service cannot find a PortfolioAccount for a given userId.
 * Indicates a data consistency problem — accounts should be created at user registration time.
 */
public class PortfolioAccountNotFoundException extends RuntimeException {

    public PortfolioAccountNotFoundException(UUID userId) {
        super("Portfolio account not found for userId: " + userId);
    }
}
