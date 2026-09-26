import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive Control Flow Graph (CFG) for target class:
 *   org.apache.commons.lang3.math.NumberUtils
 *
 * Models both the primary fault method createNumber(String) and supporting
 * methods (isNumber, min, max, type conversions, direct creators) to achieve
 * state-of-the-art branch coverage, fault detection, and test efficiency
 * in compliance with SQA Round 2 specification.
 */
public class ControlFlowGraph {

    public final List<Edge> edges = new ArrayList<>();
    public final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
    public final Map<String, String> leafDescriptions = new LinkedHashMap<>();
    public final String rootNode = "D_ROOT";

    public ControlFlowGraph() {
        // --- Root Dispatch to Functional Domains ---
        addEdge("e_root_cn", "D_ROOT", "D_CN");
        addEdge("e_root_is", "D_ROOT", "D_IS");
        addEdge("e_root_cv", "D_ROOT", "D_CV");
        addEdge("e_root_mm", "D_ROOT", "D_MM");
        addEdge("e_root_cr", "D_ROOT", "D_CR");

        // =====================================================================
        // 1. createNumber(String) Decision Subgraph
        // =====================================================================
        addEdge("e_cn_null", "D_CN", "CN_NULL");
        addEdge("e_cn_notnull", "D_CN", "D_CN_BLANK");

        addEdge("e_cn_blank_empty", "D_CN_BLANK", "CN_BLANK_EMPTY");
        addEdge("e_cn_blank_space", "D_CN_BLANK", "CN_BLANK_SPACE");
        addEdge("e_cn_notblank", "D_CN_BLANK", "D_CN_HEX");

        // Hex Branch
        addEdge("e_cn_hex_match", "D_CN_HEX", "D_CN_HEX_PFX");
        addEdge("e_cn_hex_nomatch", "D_CN_HEX", "D_CN_EXP");

        addEdge("e_cn_hex_0x", "D_CN_HEX_PFX", "CN_HEX_0x");
        addEdge("e_cn_hex_0X", "D_CN_HEX_PFX", "CN_HEX_0X");
        addEdge("e_cn_hex_hash", "D_CN_HEX_PFX", "CN_HEX_HASH");
        addEdge("e_cn_hex_neg_0x", "D_CN_HEX_PFX", "CN_HEX_NEG_0x");
        addEdge("e_cn_hex_neg_0X", "D_CN_HEX_PFX", "CN_HEX_NEG_0X");
        addEdge("e_cn_hex_neg_hash", "D_CN_HEX_PFX", "CN_HEX_NEG_HASH");
        addEdge("e_cn_hex_long", "D_CN_HEX_PFX", "CN_HEX_LONG");
        addEdge("e_cn_hex_bigint", "D_CN_HEX_PFX", "CN_HEX_BIGINT");

        // LANG-747 Fault Triggers
        addEdge("e_cn_l747_int_fault", "D_CN_HEX_PFX", "CN_LANG747_INT_FAULT");
        addEdge("e_cn_l747_long_fault", "D_CN_HEX_PFX", "CN_LANG747_LONG_FAULT");
        addEdge("e_cn_l747_zero_int", "D_CN_HEX_PFX", "CN_LANG747_ZERO_INT");
        addEdge("e_cn_l747_zero_long", "D_CN_HEX_PFX", "CN_LANG747_ZERO_LONG");

        // Exponent Branch
        addEdge("e_cn_exp_match", "D_CN_EXP", "D_CN_EXP_CASES");
        addEdge("e_cn_exp_nomatch", "D_CN_EXP", "D_CN_SUFFIX");

        addEdge("e_cn_exp_dec", "D_CN_EXP_CASES", "CN_EXP_DEC");
        addEdge("e_cn_exp_int", "D_CN_EXP_CASES", "CN_EXP_INT");
        addEdge("e_cn_exp_neg", "D_CN_EXP_CASES", "CN_EXP_NEG");
        addEdge("e_cn_exp_err_double", "D_CN_EXP_CASES", "CN_EXP_ERR_DOUBLE");
        addEdge("e_cn_exp_err_dot", "D_CN_EXP_CASES", "CN_EXP_ERR_DOT");
        addEdge("e_cn_exp_err_end", "D_CN_EXP_CASES", "CN_EXP_ERR_END");

        // Suffix Branch
        addEdge("e_cn_suf_match", "D_CN_SUFFIX", "D_CN_SUF_CASES");
        addEdge("e_cn_suf_nomatch", "D_CN_SUFFIX", "D_CN_PLAIN_DEC");

        // Suffix L
        addEdge("e_cn_suf_l_pos", "D_CN_SUF_CASES", "CN_SUF_L_POS");
        addEdge("e_cn_suf_l_neg", "D_CN_SUF_CASES", "CN_SUF_L_NEG");
        addEdge("e_cn_suf_l_bigint", "D_CN_SUF_CASES", "CN_SUF_L_BIGINT");
        addEdge("e_cn_suf_l_err", "D_CN_SUF_CASES", "CN_SUF_L_ERR");

        // Suffix F
        addEdge("e_cn_suf_f_pos", "D_CN_SUF_CASES", "CN_SUF_F_POS");
        addEdge("e_cn_suf_f_neg", "D_CN_SUF_CASES", "CN_SUF_F_NEG");
        addEdge("e_cn_suf_f_zero", "D_CN_SUF_CASES", "CN_SUF_F_ZERO");
        addEdge("e_cn_suf_f_overflow", "D_CN_SUF_CASES", "CN_SUF_F_OVERFLOW");
        addEdge("e_cn_suf_f_precision", "D_CN_SUF_CASES", "CN_SUF_F_PRECISION");
        addEdge("e_cn_suf_f_err", "D_CN_SUF_CASES", "CN_SUF_F_ERR");

        // Suffix D
        addEdge("e_cn_suf_d_pos", "D_CN_SUF_CASES", "CN_SUF_D_POS");
        addEdge("e_cn_suf_d_neg", "D_CN_SUF_CASES", "CN_SUF_D_NEG");
        addEdge("e_cn_suf_d_zero", "D_CN_SUF_CASES", "CN_SUF_D_ZERO");
        addEdge("e_cn_suf_d_overflow", "D_CN_SUF_CASES", "CN_SUF_D_OVERFLOW");
        addEdge("e_cn_suf_d_precision", "D_CN_SUF_CASES", "CN_SUF_D_PRECISION");
        addEdge("e_cn_suf_d_err", "D_CN_SUF_CASES", "CN_SUF_D_ERR");
        addEdge("e_cn_suf_err_char", "D_CN_SUF_CASES", "CN_SUF_ERR_CHAR");

        // Plain numbers & Decimals without Suffix
        addEdge("e_cn_plain", "D_CN_PLAIN_DEC", "D_CN_PLAIN_CASES");
        addEdge("e_cn_dec", "D_CN_PLAIN_DEC", "D_CN_DEC_CASES");

        addEdge("e_cn_plain_int", "D_CN_PLAIN_CASES", "CN_PLAIN_INT");
        addEdge("e_cn_plain_zero", "D_CN_PLAIN_CASES", "CN_PLAIN_ZERO");
        addEdge("e_cn_plain_long_ovf", "D_CN_PLAIN_CASES", "CN_PLAIN_LONG_OVERFLOW");
        addEdge("e_cn_plain_bigint_ovf", "D_CN_PLAIN_CASES", "CN_PLAIN_BIGINT_OVERFLOW");

        addEdge("e_cn_dec_float", "D_CN_DEC_CASES", "CN_DEC_FLOAT");
        addEdge("e_cn_dec_double", "D_CN_DEC_CASES", "CN_DEC_DOUBLE");
        addEdge("e_cn_dec_bigdec", "D_CN_DEC_CASES", "CN_DEC_BIGDEC");
        addEdge("e_cn_dec_float_ovf", "D_CN_DEC_CASES", "CN_DEC_FLOAT_OVERFLOW");
        addEdge("e_cn_dec_double_ovf", "D_CN_DEC_CASES", "CN_DEC_DOUBLE_OVERFLOW");
        addEdge("e_cn_dec_all_zeros", "D_CN_DEC_CASES", "CN_DEC_ALL_ZEROS");

        // =====================================================================
        // 2. isNumber(String) Decision Subgraph
        // =====================================================================
        addEdge("e_is_null_empty", "D_IS", "IS_NULL_EMPTY");
        addEdge("e_is_hex_valid", "D_IS", "IS_HEX_VALID");
        addEdge("e_is_hex_invalid", "D_IS", "IS_HEX_INVALID");
        addEdge("e_is_dec_signs", "D_IS", "IS_DEC_SIGNS");
        addEdge("e_is_dec_invalid", "D_IS", "IS_DEC_INVALID");
        addEdge("e_is_octal", "D_IS", "IS_OCTAL");
        addEdge("e_is_exp_valid", "D_IS", "IS_EXP_VALID");
        addEdge("e_is_exp_invalid", "D_IS", "IS_EXP_INVALID");
        addEdge("e_is_qualifiers", "D_IS", "IS_QUALIFIERS");
        addEdge("e_is_invalid_chars", "D_IS", "IS_INVALID_CHARS");

        // =====================================================================
        // 3. Conversions Decision Subgraph
        // =====================================================================
        addEdge("e_cv_int", "D_CV", "CV_TO_INT");
        addEdge("e_cv_long", "D_CV", "CV_TO_LONG");
        addEdge("e_cv_float", "D_CV", "CV_TO_FLOAT");
        addEdge("e_cv_double", "D_CV", "CV_TO_DOUBLE");
        addEdge("e_cv_byte", "D_CV", "CV_TO_BYTE");
        addEdge("e_cv_short", "D_CV", "CV_TO_SHORT");

        // =====================================================================
        // 4. Min / Max Decision Subgraph
        // =====================================================================
        addEdge("e_mm_long_arr", "D_MM", "MM_LONG_ARR");
        addEdge("e_mm_int_arr", "D_MM", "MM_INT_ARR");
        addEdge("e_mm_short_arr", "D_MM", "MM_SHORT_ARR");
        addEdge("e_mm_byte_arr", "D_MM", "MM_BYTE_ARR");
        addEdge("e_mm_double_arr", "D_MM", "MM_DOUBLE_ARR");
        addEdge("e_mm_float_arr", "D_MM", "MM_FLOAT_ARR");
        addEdge("e_mm_3arg_int", "D_MM", "MM_3ARG_INT");
        addEdge("e_mm_3arg_long", "D_MM", "MM_3ARG_LONG");
        addEdge("e_mm_3arg_short", "D_MM", "MM_3ARG_SHORT");
        addEdge("e_mm_3arg_byte", "D_MM", "MM_3ARG_BYTE");
        addEdge("e_mm_3arg_double", "D_MM", "MM_3ARG_DOUBLE");
        addEdge("e_mm_3arg_float", "D_MM", "MM_3ARG_FLOAT");

        // =====================================================================
        // 5. Direct Creators & Utilities
        // =====================================================================
        addEdge("e_cr_int_long", "D_CR", "CR_INT_LONG");
        addEdge("e_cr_float_double", "D_CR", "CR_FLOAT_DOUBLE");
        addEdge("e_cr_bigdec", "D_CR", "CR_BIGDEC");
        addEdge("e_cr_bigint", "D_CR", "CR_BIGINT");
        addEdge("e_cr_is_digits", "D_CR", "CR_IS_DIGITS");
        addEdge("e_cr_constructor", "D_CR", "CR_CONSTRUCTOR");

        // =====================================================================
        // Leaf Descriptions
        // =====================================================================
        // createNumber
        leafDescriptions.put("CN_NULL", "createNumber(null) -> return null");
        leafDescriptions.put("CN_BLANK_EMPTY", "createNumber(\"\") -> throws NumberFormatException");
        leafDescriptions.put("CN_BLANK_SPACE", "createNumber(\"   \") -> throws NumberFormatException");
        leafDescriptions.put("CN_HEX_0x", "createNumber hex 0x prefix <= 8 digits -> Integer");
        leafDescriptions.put("CN_HEX_0X", "createNumber hex 0X uppercase prefix -> Integer");
        leafDescriptions.put("CN_HEX_HASH", "createNumber hex # prefix -> Integer");
        leafDescriptions.put("CN_HEX_NEG_0x", "createNumber hex -0x prefix -> negative Integer");
        leafDescriptions.put("CN_HEX_NEG_0X", "createNumber hex -0X prefix -> negative Integer");
        leafDescriptions.put("CN_HEX_NEG_HASH", "createNumber hex -# prefix -> negative Integer");
        leafDescriptions.put("CN_HEX_LONG", "createNumber hex 9-16 digits -> Long");
        leafDescriptions.put("CN_HEX_BIGINT", "createNumber hex >16 digits -> BigInteger");
        leafDescriptions.put("CN_LANG747_INT_FAULT", "LANG-747 trigger: hex 8 digits starting with 8..F (0x80000000) -> bug throws NFE, fixed returns Long");
        leafDescriptions.put("CN_LANG747_LONG_FAULT", "LANG-747 trigger: hex 16 digits starting with 8..F (0x8000000000000000) -> bug throws NFE, fixed returns BigInteger");
        leafDescriptions.put("CN_LANG747_ZERO_INT", "LANG-747 trigger: leading zeros push hex length > 8, significant fits Integer -> bug returns Long");
        leafDescriptions.put("CN_LANG747_ZERO_LONG", "LANG-747 trigger: leading zeros push hex length > 16, significant fits Long -> bug returns BigInteger");
        leafDescriptions.put("CN_EXP_DEC", "createNumber decimal with exponent (\"1.5e3\")");
        leafDescriptions.put("CN_EXP_INT", "createNumber integer with exponent (\"15e2\")");
        leafDescriptions.put("CN_EXP_NEG", "createNumber exponent with negative power (\"1.5e-3\")");
        leafDescriptions.put("CN_EXP_ERR_DOUBLE", "createNumber double exponent (\"1.2e3e4\") -> throws NFE");
        leafDescriptions.put("CN_EXP_ERR_DOT", "createNumber exponent before dot (\"1e.5\") -> throws NFE");
        leafDescriptions.put("CN_EXP_ERR_END", "createNumber exponent at string end (\"12e\") -> throws NFE");
        leafDescriptions.put("CN_SUF_L_POS", "createNumber positive suffix L (\"12345L\") -> Long");
        leafDescriptions.put("CN_SUF_L_NEG", "createNumber negative suffix l (\"-12345l\") -> Long");
        leafDescriptions.put("CN_SUF_L_BIGINT", "createNumber suffix L exceeding Long.MAX_VALUE -> BigInteger");
        leafDescriptions.put("CN_SUF_L_ERR", "createNumber suffix L with invalid decimal (\"1.2L\") -> throws NFE");
        leafDescriptions.put("CN_SUF_F_POS", "createNumber positive suffix f (\"1.5f\") -> Float");
        leafDescriptions.put("CN_SUF_F_NEG", "createNumber negative suffix F (\"-1.5F\") -> Float");
        leafDescriptions.put("CN_SUF_F_ZERO", "createNumber zero suffix f (\"0.0f\") -> Float");
        leafDescriptions.put("CN_SUF_F_OVERFLOW", "createNumber suffix f exceeding Float range (\"1e40f\") -> Double fallback");
        leafDescriptions.put("CN_SUF_F_PRECISION", "createNumber suffix f underflow with nonzeros (\"1e-45f\") -> Double fallback");
        leafDescriptions.put("CN_SUF_F_ERR", "createNumber suffix f malformed (\"foo.f\") -> throws NFE");
        leafDescriptions.put("CN_SUF_D_POS", "createNumber positive suffix d (\"1.5d\") -> Double");
        leafDescriptions.put("CN_SUF_D_NEG", "createNumber negative suffix D (\"-1.5D\") -> Double");
        leafDescriptions.put("CN_SUF_D_ZERO", "createNumber zero suffix d (\"0.0d\") -> Double");
        leafDescriptions.put("CN_SUF_D_OVERFLOW", "createNumber suffix d exceeding Double range (\"1e400d\") -> BigDecimal fallback");
        leafDescriptions.put("CN_SUF_D_PRECISION", "createNumber suffix d underflow with nonzeros (\"1e-350d\") -> BigDecimal fallback");
        leafDescriptions.put("CN_SUF_D_ERR", "createNumber suffix d malformed (\"foo.d\") -> throws NFE");
        leafDescriptions.put("CN_SUF_ERR_CHAR", "createNumber invalid suffix character (\"123!\") -> throws NFE");
        leafDescriptions.put("CN_PLAIN_INT", "createNumber plain integer (\"-65174\") -> Integer");
        leafDescriptions.put("CN_PLAIN_ZERO", "createNumber plain zero (\"0\") -> Integer");
        leafDescriptions.put("CN_PLAIN_LONG_OVERFLOW", "createNumber plain integer overflow (\"3000000000\") -> Long fallback");
        leafDescriptions.put("CN_PLAIN_BIGINT_OVERFLOW", "createNumber plain long overflow -> BigInteger fallback");
        leafDescriptions.put("CN_DEC_FLOAT", "createNumber decimal <=7 decimals (\"0.49\") -> Float");
        leafDescriptions.put("CN_DEC_DOUBLE", "createNumber decimal 8-16 decimals (\"3.697317372068\") -> Double");
        leafDescriptions.put("CN_DEC_BIGDEC", "createNumber decimal >16 decimals -> BigDecimal");
        leafDescriptions.put("CN_DEC_FLOAT_OVERFLOW", "createNumber decimal <=7 decimals overflowing Float (\"1e40\") -> Double fallback");
        leafDescriptions.put("CN_DEC_DOUBLE_OVERFLOW", "createNumber decimal <=16 decimals overflowing Double (\"1e400\") -> BigDecimal fallback");
        leafDescriptions.put("CN_DEC_ALL_ZEROS", "createNumber decimal all zeros (\"0.0000\") -> Float");

        // isNumber
        leafDescriptions.put("IS_NULL_EMPTY", "isNumber null, empty, whitespace strings -> false");
        leafDescriptions.put("IS_HEX_VALID", "isNumber valid hex with 0x, 0X, #, negative -> true");
        leafDescriptions.put("IS_HEX_INVALID", "isNumber invalid hex prefixes and characters -> false");
        leafDescriptions.put("IS_DEC_SIGNS", "isNumber decimals, leading dots, and signs -> true");
        leafDescriptions.put("IS_DEC_INVALID", "isNumber invalid dots and misplaced signs -> false");
        leafDescriptions.put("IS_OCTAL", "isNumber octal notation numbers -> true/false branches");
        leafDescriptions.put("IS_EXP_VALID", "isNumber valid scientific notation exponents -> true");
        leafDescriptions.put("IS_EXP_INVALID", "isNumber malformed scientific exponents -> false");
        leafDescriptions.put("IS_QUALIFIERS", "isNumber valid numeric type qualifiers L, f, d -> true");
        leafDescriptions.put("IS_INVALID_CHARS", "isNumber invalid alphabetic characters and misplaced operators -> false");

        // Conversions
        leafDescriptions.put("CV_TO_INT", "toInt string conversion with default values on null/invalid");
        leafDescriptions.put("CV_TO_LONG", "toLong string conversion with default values on null/invalid");
        leafDescriptions.put("CV_TO_FLOAT", "toFloat string conversion with default values on null/invalid");
        leafDescriptions.put("CV_TO_DOUBLE", "toDouble string conversion with default values on null/invalid");
        leafDescriptions.put("CV_TO_BYTE", "toByte string conversion with default values on null/invalid");
        leafDescriptions.put("CV_TO_SHORT", "toShort string conversion with default values on null/invalid");

        // MinMax
        leafDescriptions.put("MM_LONG_ARR", "min/max long[] array with null and empty boundary checks");
        leafDescriptions.put("MM_INT_ARR", "min/max int[] array with null and empty boundary checks");
        leafDescriptions.put("MM_SHORT_ARR", "min/max short[] array with null and empty boundary checks");
        leafDescriptions.put("MM_BYTE_ARR", "min/max byte[] array with null and empty boundary checks");
        leafDescriptions.put("MM_DOUBLE_ARR", "min/max double[] array with null, empty, and NaN checks");
        leafDescriptions.put("MM_FLOAT_ARR", "min/max float[] array with null, empty, and NaN checks");
        leafDescriptions.put("MM_3ARG_INT", "min/max 3-argument int with all permutations (a<b<c, b<a<c, c<a<b)");
        leafDescriptions.put("MM_3ARG_LONG", "min/max 3-argument long with all permutations");
        leafDescriptions.put("MM_3ARG_SHORT", "min/max 3-argument short with all permutations");
        leafDescriptions.put("MM_3ARG_BYTE", "min/max 3-argument byte with all permutations");
        leafDescriptions.put("MM_3ARG_DOUBLE", "min/max 3-argument double with permutations and NaN");
        leafDescriptions.put("MM_3ARG_FLOAT", "min/max 3-argument float with permutations and NaN");

        // Direct Creators
        leafDescriptions.put("CR_INT_LONG", "createInteger and createLong with valid and null values");
        leafDescriptions.put("CR_FLOAT_DOUBLE", "createFloat and createDouble with valid and null values");
        leafDescriptions.put("CR_BIGDEC", "createBigDecimal with valid, null, and blank checks");
        leafDescriptions.put("CR_BIGINT", "createBigInteger with decimal, hex 0x/#, octal 0, negative, and null");
        leafDescriptions.put("CR_IS_DIGITS", "isDigits with valid digits, null, empty, and non-digit strings");
        leafDescriptions.put("CR_CONSTRUCTOR", "NumberUtils class constructor instantiation");
    }

    private void addEdge(String id, String from, String to) {
        Edge e = new Edge(id, from, to);
        edges.add(e);
        adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(e);
    }

    public boolean isLeaf(String nodeOrLeaf) {
        return leafDescriptions.containsKey(nodeOrLeaf);
    }

    public List<String> leaves() {
        return new ArrayList<>(leafDescriptions.keySet());
    }

    public List<String> reachableLeaves(String nodeOrLeaf) {
        List<String> result = new ArrayList<>();
        collectReachable(nodeOrLeaf, result);
        return result;
    }

    private void collectReachable(String nodeOrLeaf, List<String> result) {
        if (isLeaf(nodeOrLeaf)) {
            result.add(nodeOrLeaf);
            return;
        }
        for (Edge e : adjacency.getOrDefault(nodeOrLeaf, new ArrayList<>())) {
            collectReachable(e.to, result);
        }
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Control Flow Graph: org.apache.commons.lang3.math.NumberUtils\n");
        sb.append("Root node: ").append(rootNode).append("\n\n");
        sb.append("Edges:\n");
        for (Edge e : edges) {
            sb.append(String.format("  %-25s %s -> %s%n", e.id, e.from, e.to));
        }
        sb.append("\nLeaves (target branches):\n");
        for (Map.Entry<String, String> entry : leafDescriptions.entrySet()) {
            sb.append(String.format("  %-25s %s%n", entry.getKey(), entry.getValue()));
        }
        return sb.toString();
    }
}
