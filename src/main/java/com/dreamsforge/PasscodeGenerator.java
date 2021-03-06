package com.dreamsforge;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * An implementation of the HOTP generator specified by RFC 4226. Generates
 * short passcodes that may be used in challenge-response protocols or as
 * timeout passcodes that are only valid for a short period.
 *
 * The default passcode is a 6-digit decimal code. The maximum passcode length is 9 digits.
 *
 * @author sweis@google.com (Steve Weis)
 *
 */
public class PasscodeGenerator {
  private static final int MAX_PASSCODE_LENGTH = 9;

  /** Default decimal passcode length */
  private static final int PASS_CODE_LENGTH = 6;

  /** Powers of 10 used to shorten the pin to the desired number of digits */
  private static final int[] DIGITS_POWER
      // 0 1  2   3    4     5      6       7        8         9
      = {1,10,100,1000,10000,100000,1000000,10000000,100000000,1000000000};

  private final Signer signer;
  private final int codeLength;

  /**
   * Using an interface to allow us to inject different signature
   * implementations.
   */
  interface Signer {
    /**
     * @param data Pre image to sign, represented as sequence of arbitrary bytes
     * @return Signature as sequence of bytes.
     * @throws GeneralSecurityException lança de vez em quando
     */
    byte[] sign(byte[] data) throws GeneralSecurityException;
  }

  /**
   * @param mac A {@link Mac} used to generate passcodes
   */
  public PasscodeGenerator(Mac mac) {
    this(mac, PASS_CODE_LENGTH);
  }

  public PasscodeGenerator(Signer signer) {
    this(signer, PASS_CODE_LENGTH);
  }

  /**
   * @param mac A {@link Mac} used to generate passcodes
   * @param passCodeLength The length of the decimal passcode
   */
  public PasscodeGenerator(final Mac mac, int passCodeLength) {
    this(mac::doFinal, passCodeLength);
  }

  public PasscodeGenerator(Signer signer, int passCodeLength) {
    if ((passCodeLength < 0) || (passCodeLength > MAX_PASSCODE_LENGTH)) {
      throw new IllegalArgumentException(
        "PassCodeLength must be between 1 and " + MAX_PASSCODE_LENGTH
        + " digits.");
    }
    this.signer = signer;
    this.codeLength = passCodeLength;
  }

  private String padOutput(int value) {
    StringBuilder result = new StringBuilder(Integer.toString(value));
    for (int i = result.length(); i < codeLength; i++) {
      result.insert(0, "0");
    }
    return result.toString();
  }

  /**
   * @param state 8-byte integer value representing internal OTP state.
   * @return A decimal response code
   * @throws GeneralSecurityException If a JCE exception occur
   */
  String generateResponseCode(long state)
      throws GeneralSecurityException {
    byte[] value = ByteBuffer.allocate(8).putLong(state).array();
    return generateResponseCode(value);
  }


  /**
   * @param challenge An arbitrary byte array used as a challenge
   * @return A decimal response code
   * @throws GeneralSecurityException If a JCE exception occur
   */
  private String generateResponseCode(byte[] challenge)
      throws GeneralSecurityException {
    byte[] hash = signer.sign(challenge);

    // Dynamically truncate the hash
    // OffsetBits are the low order bits of the last byte of the hash
    int offset = hash[hash.length - 1] & 0xF;
    // Grab a positive integer value starting at the given offset.
    int truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
    int pinValue = truncatedHash % DIGITS_POWER[codeLength];
    return padOutput(pinValue);
  }

  /**
   * Grabs a positive integer value from the input array starting at
   * the given offset.
   * @param bytes the array of bytes
   * @param start the index into the array to start grabbing bytes
   * @return the integer constructed from the four bytes in the array
   */
  private int hashToInt(byte[] bytes, int start) {
    DataInput input = new DataInputStream(
        new ByteArrayInputStream(bytes, start, bytes.length - start));
    int val;
    try {
      val = input.readInt();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return val;
  }

}
