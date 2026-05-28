package com.multirkh.chimhahaimage.common.util;

import java.security.SecureRandom;

public final class IdGenerator {
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 20;
    private static final SecureRandom random = new SecureRandom();

    public static String generateUniqueId() {
        return generateUniqueId(ID_LENGTH);
    }

    public static String generateUniqueId(int idLength) {
        StringBuilder id = new StringBuilder(idLength);
        for (int i = 0; i < idLength; i++) {
            int index = random.nextInt(CHARACTERS.length());
            id.append(CHARACTERS.charAt(index));
        }
        return id.toString();
    }
}
