package com.getbouncer.cardscan;

public class CreditCardUtils {
    // https://en.wikipedia.org/wiki/Luhn_algorithm#Java
    static boolean luhnCheck(String ccNumber) {
        if (ccNumber == null || ccNumber.length() == 0) {
            return false;
        } else if (!isValidBin(ccNumber)) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = ccNumber.length() - 1; i >= 0; i--)
        {
            int n = Integer.parseInt(ccNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static String format(String number) {
        if (number.length() == 16) {
            return format16(number);
        } else if (number.length() == 15) {
            return format15(number);
        }

        return number;
    }

    private static String format16(String number) {
        StringBuilder result = new StringBuilder();
        for (int idx = 0; idx < number.length(); idx++) {
            if (idx == 4 || idx == 8 || idx == 12) {
                result.append(" ");
            }
            result.append(number.charAt(idx));
        }

        return result.toString();
    }

    private static String format15(String number) {
        StringBuilder result = new StringBuilder();
        for (int idx = 0; idx < number.length(); idx++) {
            if (idx == 4 || idx == 10) {
                result.append(" ");
            }
            result.append(number.charAt(idx));
        }

        return result.toString();
    }

    private static boolean isValidBin(String number) {
        return isAmex(number) || isDiscover(number) || isVisa(number) || isMastercard(number);
    }

    private static String prefix(String s, int n) {
        String result = "";
        while (result.length() < n) {
            result += s.charAt(result.length());
        }

        return result;
    }

    static boolean isAmex(String number) {
        int prefix = Integer.parseInt(prefix(number,2));

        return number.length() == 15 && (prefix == 34 || prefix == 37);
    }

    static boolean isDiscover(String number) {
        int prefix2 = Integer.parseInt(prefix(number,2));
        int prefix4 = Integer.parseInt(prefix(number, 4));
        int prefix6 = Integer.parseInt(prefix(number, 6));

        return prefix2 == 64 || prefix2 == 65 || prefix4 == 6011 ||
                (prefix6 >= 622126 && prefix6 <= 622925) ||
                (prefix6 >= 624000 && prefix6 <= 626999) ||
                (prefix6 >= 628200 && prefix6 <= 628899);
    }

    static boolean isMastercard(String number) {
        int prefix2 = Integer.parseInt(prefix(number, 2));
        int prefix4 = Integer.parseInt(prefix(number, 4));

        if (number.length() != 16) {
            return false;
        }

        return (prefix2 >= 51 && prefix2 <= 55) || (prefix4 >= 2221 && prefix4 <= 2720);
    }

    static boolean isVisa(String number) {
        return (number.length() == 16) && number.startsWith("4");
    }
}
