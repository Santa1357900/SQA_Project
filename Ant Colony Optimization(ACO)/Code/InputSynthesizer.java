import java.util.Random;

/**
 * Given a target leaf id, produces a concrete candidate input string or token
 * that should reach that leaf. This represents the "Input Generation" step
 * in the ACO workflow (SQA Round 2 Workflow section 12.4).
 */
public class InputSynthesizer {

    private static final String DIGITS = "0123456789";
    private static final String HEX_DIGITS = "0123456789abcdefABCDEF";
    private static final String HEX_DIGITS_NONZERO = "123456789abcdefABCDEF";

    private static String randDigits(int n, Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(DIGITS.charAt(rng.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }

    private static String randHexDigits(int n, Random rng) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(HEX_DIGITS.charAt(rng.nextInt(HEX_DIGITS.length())));
        }
        return sb.toString();
    }

    private static String randHexDigitsNoLeadingZero(int n, Random rng) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(HEX_DIGITS_NONZERO.charAt(rng.nextInt(HEX_DIGITS_NONZERO.length())));
        for (int i = 1; i < n; i++) {
            sb.append(HEX_DIGITS.charAt(rng.nextInt(HEX_DIGITS.length())));
        }
        return sb.toString();
    }

    private static int randBetween(int min, int max, Random rng) {
        return min + rng.nextInt(max - min + 1);
    }

    @SafeVarargs
    private static <T> T choice(Random rng, T... options) {
        return options[rng.nextInt(options.length)];
    }

    public static String synthesize(String leafId, Random rng) {
        switch (leafId) {
            // --- createNumber: null & blanks ---
            case "CN_NULL":
                return null;
            case "CN_BLANK_EMPTY":
                return "";
            case "CN_BLANK_SPACE":
                return choice(rng, "   ", "\t", " \t ");

            // --- createNumber: hex prefixes & ranges ---
            case "CN_HEX_0x":
                return "0x" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_0X":
                return "0X" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_HASH":
                return "#" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_NEG_0x":
                return "-0x" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_NEG_0X":
                return "-0X" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_NEG_HASH":
                return "-#" + randHexDigits(randBetween(1, 4, rng), rng);
            case "CN_HEX_LONG":
                return "0x" + randHexDigits(randBetween(9, 14, rng), rng);
            case "CN_HEX_BIGINT":
                return "0x" + randHexDigits(randBetween(17, 20, rng), rng);

            // --- createNumber: LANG-747 fault detection triggers ---
            case "CN_LANG747_INT_FAULT":
                // 8 hex digits, first digit is 8..F -> bug wrongly calls createInteger, throwing NumberFormatException
                return "0x8" + randHexDigits(7, rng);
            case "CN_LANG747_LONG_FAULT":
                // 16 hex digits, first digit is 8..F -> bug wrongly calls createLong, throwing NumberFormatException
                return "0x8" + randHexDigits(15, rng);
            case "CN_LANG747_ZERO_INT": {
                int significant = randBetween(1, 8, rng);
                int zeros = randBetween(Math.max(1, 9 - significant), 16 - significant, rng);
                return "0x" + "0".repeat(zeros) + randHexDigitsNoLeadingZero(significant, rng);
            }
            case "CN_LANG747_ZERO_LONG": {
                int significant = randBetween(1, 16, rng);
                int zeros = randBetween(Math.max(1, 17 - significant), 24 - significant, rng);
                return "0x" + "0".repeat(zeros) + randHexDigitsNoLeadingZero(significant, rng);
            }

            // --- createNumber: exponents ---
            case "CN_EXP_DEC":
                return randDigits(1, rng) + "." + randDigits(randBetween(1, 2, rng), rng) + choice(rng, "e", "E") + randDigits(randBetween(1, 3, rng), rng);
            case "CN_EXP_INT":
                return randDigits(randBetween(1, 2, rng), rng) + choice(rng, "e", "E") + randDigits(randBetween(1, 3, rng), rng);
            case "CN_EXP_NEG":
                return randDigits(1, rng) + "." + randDigits(1, rng) + choice(rng, "e", "E") + "-" + randDigits(randBetween(1, 2, rng), rng);
            case "CN_EXP_ERR_DOUBLE":
                return "1.2e3e4";
            case "CN_EXP_ERR_DOT":
                return "1e.5";
            case "CN_EXP_ERR_END":
                return choice(rng, "12e", "5E");

            // --- createNumber: Suffix L ---
            case "CN_SUF_L_POS":
                return randDigits(randBetween(1, 5, rng), rng) + choice(rng, "L", "l");
            case "CN_SUF_L_NEG":
                return "-" + randDigits(randBetween(1, 5, rng), rng) + choice(rng, "L", "l");
            case "CN_SUF_L_BIGINT":
                return "999999999999999999999999999999" + choice(rng, "L", "l");
            case "CN_SUF_L_ERR":
                return "1.2L";

            // --- createNumber: Suffix F ---
            case "CN_SUF_F_POS":
                return randDigits(1, rng) + "." + randDigits(randBetween(1, 2, rng), rng) + choice(rng, "f", "F");
            case "CN_SUF_F_NEG":
                return "-" + randDigits(1, rng) + "." + randDigits(randBetween(1, 2, rng), rng) + choice(rng, "f", "F");
            case "CN_SUF_F_ZERO":
                return "0.0" + choice(rng, "f", "F");
            case "CN_SUF_F_OVERFLOW":
                return "1e40" + choice(rng, "f", "F");
            case "CN_SUF_F_PRECISION":
                return "1e-45" + choice(rng, "f", "F");
            case "CN_SUF_F_ERR":
                return "foo.f";

            // --- createNumber: Suffix D ---
            case "CN_SUF_D_POS":
                return randDigits(1, rng) + "." + randDigits(randBetween(1, 2, rng), rng) + choice(rng, "d", "D");
            case "CN_SUF_D_NEG":
                return "-" + randDigits(1, rng) + "." + randDigits(randBetween(1, 2, rng), rng) + choice(rng, "d", "D");
            case "CN_SUF_D_ZERO":
                return "0.0" + choice(rng, "d", "D");
            case "CN_SUF_D_OVERFLOW":
                return "1e400" + choice(rng, "d", "D");
            case "CN_SUF_D_PRECISION":
                return "1e-350" + choice(rng, "d", "D");
            case "CN_SUF_D_ERR":
                return "foo.d";
            case "CN_SUF_ERR_CHAR":
                return randDigits(randBetween(1, 3, rng), rng) + choice(rng, "!", "z", "q");

            // --- createNumber: Plain Numbers ---
            case "CN_PLAIN_INT":
                return choice(rng, "", "-") + randDigits(randBetween(1, 5, rng), rng);
            case "CN_PLAIN_ZERO":
                return "0";
            case "CN_PLAIN_LONG_OVERFLOW":
                return "3000000000";
            case "CN_PLAIN_BIGINT_OVERFLOW":
                return "999999999999999999999999999999";

            // --- createNumber: Decimals without Suffix ---
            case "CN_DEC_FLOAT":
                return randDigits(1, rng) + "." + randDigits(randBetween(1, 5, rng), rng);
            case "CN_DEC_DOUBLE":
                return randDigits(1, rng) + "." + randDigits(randBetween(8, 14, rng), rng);
            case "CN_DEC_BIGDEC":
                return randDigits(1, rng) + "." + randDigits(randBetween(17, 20, rng), rng);
            case "CN_DEC_FLOAT_OVERFLOW":
                return "1e40";
            case "CN_DEC_DOUBLE_OVERFLOW":
                return "1e400";
            case "CN_DEC_ALL_ZEROS":
                return "0.0000";

            // --- Other Target Domain Tokens ---
            case "IS_NULL_EMPTY":
            case "IS_HEX_VALID":
            case "IS_HEX_INVALID":
            case "IS_DEC_SIGNS":
            case "IS_DEC_INVALID":
            case "IS_OCTAL":
            case "IS_EXP_VALID":
            case "IS_EXP_INVALID":
            case "IS_QUALIFIERS":
            case "IS_INVALID_CHARS":
            case "CV_TO_INT":
            case "CV_TO_LONG":
            case "CV_TO_FLOAT":
            case "CV_TO_DOUBLE":
            case "CV_TO_BYTE":
            case "CV_TO_SHORT":
            case "MM_LONG_ARR":
            case "MM_INT_ARR":
            case "MM_SHORT_ARR":
            case "MM_BYTE_ARR":
            case "MM_DOUBLE_ARR":
            case "MM_FLOAT_ARR":
            case "MM_3ARG_INT":
            case "MM_3ARG_LONG":
            case "MM_3ARG_SHORT":
            case "MM_3ARG_BYTE":
            case "MM_3ARG_DOUBLE":
            case "MM_3ARG_FLOAT":
            case "CR_INT_LONG":
            case "CR_FLOAT_DOUBLE":
            case "CR_BIGDEC":
            case "CR_BIGINT":
            case "CR_IS_DIGITS":
            case "CR_CONSTRUCTOR":
                return leafId; // token marker representing generated suite partition

            default:
                return null;
        }
    }
}
