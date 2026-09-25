package com.bank.util;

import org.mindrot.jbcrypt.BCrypt;

// We will Hash the Pin and Verify if it has been properly verified or not
public final class PinHasher {
    private PinHasher(){

    }
    public static String hash(String pin){
        return BCrypt.hashpw(pin, BCrypt.gensalt());
    }

    public static boolean verify(String pin, String hash){
        return BCrypt.checkpw(pin, hash);
    }
    
}
