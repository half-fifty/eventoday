package com.min.edu.payment.service;

class TicketOrderBusinessCreationFailedException extends RuntimeException {

    TicketOrderBusinessCreationFailedException(RuntimeException cause) {
        super(cause);
    }

    RuntimeException original() {
        return (RuntimeException) getCause();
    }
}
