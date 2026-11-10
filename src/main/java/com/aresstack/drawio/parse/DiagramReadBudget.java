package com.aresstack.drawio.parse;

import com.aresstack.drawio.api.DiagramFormatException;

/**
 * Enforce a single retained-output budget for all pages of a document.
 *
 * <p>Count the larger of the inflated payload and normalized graph XML
 * without charging transient and retained representations twice.
 */
final class DiagramReadBudget {

    private int remainingBytes;

    DiagramReadBudget(int maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.remainingBytes = maximumBytes;
    }

    int remainingBytes() {
        return remainingBytes;
    }

    void charge(int inflatedBytes, int graphModelCharacters) throws DiagramFormatException {
        if (inflatedBytes < 0 || graphModelCharacters < 0) {
            throw new IllegalArgumentException("Page sizes must not be negative");
        }

        int consumed = Math.max(inflatedBytes, graphModelCharacters);
        if (consumed > remainingBytes) {
            throw new DiagramFormatException(DiagramFormatException.Reason.LIMIT_EXCEEDED,
                    "The draw.io document exceeds the cumulative page budget");
        }
        remainingBytes -= consumed;
    }
}
