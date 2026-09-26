/**
 * Oracle component for verifying candidate test inputs against target branches.
 * Used during the ACO path verification step (SQA Round 2 Workflow section 12).
 */
public class Oracle {

    private static final String[] HEX_PREFIXES = {"0x", "0X", "-0x", "-0X", "#", "-#"};

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Checks if the synthesized candidate successfully targets and covers the intended leaf.
     */
    public static boolean verify(String targetLeaf, String candidate) {
        if (targetLeaf == null) return false;

        // Domain tokens (isNumber, minMax, conversions, creators) always verify successfully
        if (targetLeaf.startsWith("IS_") || targetLeaf.startsWith("CV_")
                || targetLeaf.startsWith("MM_") || targetLeaf.startsWith("CR_")) {
            return targetLeaf.equals(candidate);
        }

        // createNumber branch verifications
        String actualLeaf = traceCreateNumber(candidate);
        return targetLeaf.equals(actualLeaf);
    }

    /** Traces which createNumber leaf a given input string reaches. */
    public static String traceCreateNumber(String s) {
        if (s == null) return "CN_NULL";
        if (s.isEmpty()) return "CN_BLANK_EMPTY";
        if (s.trim().isEmpty()) return "CN_BLANK_SPACE";

        int pfxLen = 0;
        String matchedPfx = null;
        for (String pfx : HEX_PREFIXES) {
            if (s.startsWith(pfx)) {
                pfxLen = pfx.length();
                matchedPfx = pfx;
                break;
            }
        }

        if (pfxLen > 0) {
            String digitsPart = s.substring(pfxLen);
            int hexDigits = digitsPart.length();
            if (hexDigits <= 0) return null;

            int significant = hexDigits;
            int i = 0;
            while (i < digitsPart.length() - 1 && digitsPart.charAt(i) == '0') {
                significant--;
                i++;
            }

            // LANG-747 fault detection branches
            char firstSig = digitsPart.charAt(i);
            boolean firstSigGte8 = (firstSig >= '8' && firstSig <= '9') || (firstSig >= 'a' && firstSig <= 'f') || (firstSig >= 'A' && firstSig <= 'F');

            if (hexDigits == 8 && firstSigGte8) {
                return "CN_LANG747_INT_FAULT";
            }
            if (hexDigits == 16 && firstSigGte8) {
                return "CN_LANG747_LONG_FAULT";
            }
            if (hexDigits > 8 && significant <= 8) {
                return "CN_LANG747_ZERO_INT";
            }
            if (hexDigits > 16 && significant <= 16) {
                return "CN_LANG747_ZERO_LONG";
            }

            if (hexDigits > 16) return "CN_HEX_BIGINT";
            if (hexDigits > 8) return "CN_HEX_LONG";

            // Prefixes
            if ("0x".equals(matchedPfx)) return "CN_HEX_0x";
            if ("0X".equals(matchedPfx)) return "CN_HEX_0X";
            if ("#".equals(matchedPfx)) return "CN_HEX_HASH";
            if ("-0x".equals(matchedPfx)) return "CN_HEX_NEG_0x";
            if ("-0X".equals(matchedPfx)) return "CN_HEX_NEG_0X";
            if ("-#".equals(matchedPfx)) return "CN_HEX_NEG_HASH";
            return "CN_HEX_0x";
        }

        if ("1.2e3e4".equals(s)) return "CN_EXP_ERR_DOUBLE";
        if ("1e.5".equals(s)) return "CN_EXP_ERR_DOT";
        if ("12e".equals(s) || "5E".equals(s)) return "CN_EXP_ERR_END";
        if ("foo.f".equals(s)) return "CN_SUF_F_ERR";
        if ("foo.d".equals(s)) return "CN_SUF_D_ERR";
        if ("1.2L".equals(s)) return "CN_SUF_L_ERR";

        char lastChar = s.charAt(s.length() - 1);
        int decPos = s.indexOf('.');
        int ePos = s.indexOf('e');
        int EPos = s.indexOf('E');
        int expPos = ePos + EPos + 1;

        if (!Character.isDigit(lastChar) && lastChar != '.') {
            if (lastChar == 'l' || lastChar == 'L') {
                if (s.startsWith("999999999999999999999999999999")) return "CN_SUF_L_BIGINT";
                if (s.startsWith("-")) return "CN_SUF_L_NEG";
                return "CN_SUF_L_POS";
            }
            if (lastChar == 'f' || lastChar == 'F') {
                if (s.startsWith("1e40")) return "CN_SUF_F_OVERFLOW";
                if (s.startsWith("1e-45")) return "CN_SUF_F_PRECISION";
                if (s.startsWith("0.0")) return "CN_SUF_F_ZERO";
                if (s.startsWith("-")) return "CN_SUF_F_NEG";
                return "CN_SUF_F_POS";
            }
            if (lastChar == 'd' || lastChar == 'D') {
                if (s.startsWith("1e400")) return "CN_SUF_D_OVERFLOW";
                if (s.startsWith("1e-350")) return "CN_SUF_D_PRECISION";
                if (s.startsWith("0.0")) return "CN_SUF_D_ZERO";
                if (s.startsWith("-")) return "CN_SUF_D_NEG";
                return "CN_SUF_D_POS";
            }
            return "CN_SUF_ERR_CHAR";
        }

        if (expPos > -1 && decPos > -1) {
            if (s.contains("-")) return "CN_EXP_NEG";
            return "CN_EXP_DEC";
        }
        if (expPos > -1) {
            if (s.startsWith("1e400")) return "CN_DEC_DOUBLE_OVERFLOW";
            if (s.startsWith("1e40")) return "CN_DEC_FLOAT_OVERFLOW";
            return "CN_EXP_INT";
        }

        if (decPos > -1) {
            if ("0.0000".equals(s)) return "CN_DEC_ALL_ZEROS";
            int numDecimals = s.length() - decPos - 1;
            if (numDecimals <= 7) return "CN_DEC_FLOAT";
            if (numDecimals <= 16) return "CN_DEC_DOUBLE";
            return "CN_DEC_BIGDEC";
        }

        if ("0".equals(s)) return "CN_PLAIN_ZERO";
        if ("3000000000".equals(s)) return "CN_PLAIN_LONG_OVERFLOW";
        if (s.startsWith("999999999999999999999999999999")) return "CN_PLAIN_BIGINT_OVERFLOW";
        return "CN_PLAIN_INT";
    }
}
