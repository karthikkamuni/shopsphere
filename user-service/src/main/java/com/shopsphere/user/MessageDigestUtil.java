package com.shopsphere.user;

import java.security.MessageDigest;

final class MessageDigestUtil {
    private MessageDigestUtil() {}
    static boolean constantTime(byte[] a, byte[] b) { return MessageDigest.isEqual(a, b); }
}
