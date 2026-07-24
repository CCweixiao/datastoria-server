package io.datastoria.server.domain;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Minimal Crockford-base32 ULID generator (26 chars). All externally-visible resource identifiers
 * are generated in application code so INSERTs never need database-generated keys (which differ
 * between SQLite and MySQL).
 */
public final class Ulid {

  private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private Ulid() {}

  public static String next() {
    return format(Instant.now(), RANDOM);
  }

  static String format(Instant timestamp, SecureRandom random) {
    long millis = timestamp.toEpochMilli();
    byte[] entropy = new byte[10];
    random.nextBytes(entropy);

    char[] chars = new char[26];
    // time block: 48 bits -> 10 chars
    long t = millis << 16;
    chars[0] = ALPHABET[(int) ((t >>> 45) & 0x1F)];
    chars[1] = ALPHABET[(int) ((t >>> 40) & 0x1F)];
    chars[2] = ALPHABET[(int) ((t >>> 35) & 0x1F)];
    chars[3] = ALPHABET[(int) ((t >>> 30) & 0x1F)];
    chars[4] = ALPHABET[(int) ((t >>> 25) & 0x1F)];
    chars[5] = ALPHABET[(int) ((t >>> 20) & 0x1F)];
    chars[6] = ALPHABET[(int) ((t >>> 15) & 0x1F)];
    chars[7] = ALPHABET[(int) ((t >>> 10) & 0x1F)];
    chars[8] = ALPHABET[(int) ((t >>> 5) & 0x1F)];
    chars[9] = ALPHABET[(int) (t & 0x1F)];

    // entropy: 80 bits -> 16 chars
    long e =
        ((long) (entropy[0] & 0xFF) << 72)
            | ((long) (entropy[1] & 0xFF) << 64)
            | ((long) (entropy[2] & 0xFF) << 56)
            | ((long) (entropy[3] & 0xFF) << 48)
            | ((long) (entropy[4] & 0xFF) << 40)
            | ((long) (entropy[5] & 0xFF) << 32)
            | ((long) (entropy[6] & 0xFF) << 24)
            | ((long) (entropy[7] & 0xFF) << 16)
            | ((long) (entropy[8] & 0xFF) << 8)
            | (entropy[9] & 0xFF);
    for (int i = 0; i < 16; i++) {
      chars[10 + i] = ALPHABET[(int) ((e >>> (75 - 5 * i)) & 0x1F)];
    }
    return new String(chars);
  }
}
