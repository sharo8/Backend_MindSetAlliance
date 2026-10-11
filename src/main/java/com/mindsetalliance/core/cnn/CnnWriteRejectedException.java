package com.mindsetalliance.core.cnn;

/** Écriture ou verrouillage refusé avant tout envoi à Colis na Nga. */
public class CnnWriteRejectedException extends RuntimeException {

    public CnnWriteRejectedException(String message) {
        super(message);
    }
}
