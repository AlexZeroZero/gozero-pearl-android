#include "case33_gemm_xor_i8mm.hpp"

#if (defined(__aarch64__) || defined(_M_ARM64)) && defined(__ARM_FEATURE_MATMUL_INT8)
#include "case33_gemm_xor.hpp"

#include <arm_neon.h>
#include <cstdlib>
#include <cstring>
#include <vector>

namespace {
constexpr int kMR = Case33GemmXor::kMR;
constexpr int kNR = Case33GemmXor::kNR;
constexpr int kKR = Case33GemmXor::kKR;
constexpr int kPanelA = kKR * kMR;
constexpr int kPanelB = kKR * kNR;
constexpr int kKGroups = kKR / 4;
constexpr int kColsPerGroup = 8;
constexpr int kColGroups = kNR / kColsPerGroup;

static_assert(kMR == 8, "I8MM kernel assumes 8 rows per micro tile");
static_assert(kNR % kColsPerGroup == 0, "I8MM kernel assumes 8-column groups");
static_assert(kKGroups % 2 == 0, "I8MM kernel consumes two 4-k groups (8 k) per step");

/* Interleave two 4-k groups of the same 4 rows (or columns) into two 2x8 smmla operands.
 *   g0 = [x0 k0-3 | x1 k0-3 | x2 k0-3 | x3 k0-3]   (as 4 x int32)
 *   g1 = [x0 k4-7 | x1 k4-7 | x2 k4-7 | x3 k4-7]
 *   zip1 -> [x0 k0-3 | x0 k4-7 | x1 k0-3 | x1 k4-7] = rows/cols x0, x1 of 8 k
 *   zip2 -> [x2 k0-3 | x2 k4-7 | x3 k0-3 | x3 k4-7] = rows/cols x2, x3 of 8 k */
inline int8x16_t zip_lo(int8x16_t g0, int8x16_t g1) {
    return vreinterpretq_s8_s32(vzip1q_s32(vreinterpretq_s32_s8(g0), vreinterpretq_s32_s8(g1)));
}
inline int8x16_t zip_hi(int8x16_t g0, int8x16_t g1) {
    return vreinterpretq_s8_s32(vzip2q_s32(vreinterpretq_s32_s8(g0), vreinterpretq_s32_s8(g1)));
}

/* XOR all 32-bit lanes of the 16 accumulators of one 8x8 column group. */
inline uint32_t xor_accumulators(const int32x4_t (*acc)[4]) {
    int32x4_t x0 = veorq_s32(veorq_s32(acc[0][0], acc[0][1]), veorq_s32(acc[0][2], acc[0][3]));
    int32x4_t x1 = veorq_s32(veorq_s32(acc[1][0], acc[1][1]), veorq_s32(acc[1][2], acc[1][3]));
    int32x4_t x2 = veorq_s32(veorq_s32(acc[2][0], acc[2][1]), veorq_s32(acc[2][2], acc[2][3]));
    int32x4_t x3 = veorq_s32(veorq_s32(acc[3][0], acc[3][1]), veorq_s32(acc[3][2], acc[3][3]));
    const uint32x4_t x = vreinterpretq_u32_s32(veorq_s32(veorq_s32(x0, x1), veorq_s32(x2, x3)));
    const uint32x2_t h = veor_u32(vget_low_u32(x), vget_high_u32(x));
    return vget_lane_u32(h, 0) ^ vget_lane_u32(h, 1);
}
} // namespace

/* Exact s8s8 8x16 micro tile over all KR panels of one K sweep, one milestone per panel,
 * using the ARMv8.6 I8MM signed 8-bit matrix multiply-accumulate (smmla).
 *
 * smmla (vmmlaq_s32(acc, A, B)) treats A as a 2x8 int8 matrix (row 0 = bytes 0-7,
 * row 1 = bytes 8-15), B as an 8x2 int8 matrix stored column-major (col 0 = bytes 0-7,
 * col 1 = bytes 8-15) and accumulates the 2x2 int32 product row-major:
 *   acc[0] += A.row0 . B.col0   acc[1] += A.row0 . B.col1
 *   acc[2] += A.row1 . B.col0   acc[3] += A.row1 . B.col1
 * i.e. 32 MAC per instruction (sdot: 16).
 *
 * Legacy input layout (CP_I8MM_PACKED=0):
 *   A panel = kKR x kMR bytes; k-group kg (4 k) = 32 bytes = 8 rows x 4 k, row r at +4r.
 *   B panel = kKR x kNR bytes; (jg, kg) group = 32 bytes = 8 cols x 4 k, col c at +4c.
 * Two consecutive k-groups (8 k) are combined with vzip1q_s32/vzip2q_s32 (see zip_lo/zip_hi)
 * into the 2x8 operands: A rows {0,1},{2,3},{4,5},{6,7} and B cols {0,1},{2,3},{4,5},{6,7}
 * of the current 8-column group, giving 4 x 4 = 16 smmla per 8 k per column group.
 *
 * Accumulator layout: acc[rp][cp] holds
 *   [ C(2rp, 2cp)  C(2rp, 2cp+1)  C(2rp+1, 2cp)  C(2rp+1, 2cp+1) ]
 * for rows 2rp,2rp+1 and columns jg*8 + 2cp, 2cp+1. Each lane is the int32 wrap-around
 * sum of all products up to the current panel, order-independent, hence bit-identical to
 * the scalar reference's vals[]. The milestone XOR is element-wise over the 128 tile values
 * (XOR is commutative, so the lane order does not matter); the two column groups cover
 * disjoint elements, so XOR-ing the first pass' result with the second pass' result equals
 * xor_tile(vals) of the reference. Packed=true uses the same panel strides but
 * consumes four pre-zipped 2x8 operands per 64 bytes, eliminating eight ZIP
 * instructions from each inner K step. ContiguousB groups all K panels of one
 * 8-column stream together, so both A and B are sequential rather than skipping
 * alternating 1-KiB B groups. Milestones and signed MACs are unchanged. */
template<bool Packed, bool ContiguousB = false>
static void i8mm_kernel(
        const int8_t *a_base, const int8_t *b_base, int blocks_k, int blocks_per_milestone,
        int num_milestones, size_t spatial_tile_id, size_t tile_count, bool xor_after_milestone,
        uint32_t *tile_xor_out) {
    (void)blocks_per_milestone;
    (void)num_milestones;
    for (int jg = 0; jg < kColGroups; ++jg) {
        int32x4_t acc[4][4];
        for (int rp = 0; rp < 4; ++rp)
            for (int cp = 0; cp < 4; ++cp) acc[rp][cp] = vdupq_n_s32(0);

        for (int ms = 0; ms < blocks_k; ++ms) {
            const int8_t *a_tile = a_base + static_cast<size_t>(ms) * kPanelA;
            const int8_t *b_tile = ContiguousB
                ? b_base + (static_cast<size_t>(jg) * blocks_k + ms) * (kPanelB / kColGroups)
                : b_base + static_cast<size_t>(ms) * kPanelB + static_cast<size_t>(jg) * kKGroups * 32;
            for (int kg = 0; kg < kKGroups; kg += 2) {
                const int8_t *a_group = a_tile + static_cast<size_t>(kg) * 32;
                const int8_t *b_group = b_tile + static_cast<size_t>(kg) * 32;
                int8x16_t A01, A23, A45, A67, B01, B23, B45, B67;
                if constexpr (Packed) {
                    A01 = vld1q_s8(a_group + 0);
                    A23 = vld1q_s8(a_group + 16);
                    A45 = vld1q_s8(a_group + 32);
                    A67 = vld1q_s8(a_group + 48);
                    B01 = vld1q_s8(b_group + 0);
                    B23 = vld1q_s8(b_group + 16);
                    B45 = vld1q_s8(b_group + 32);
                    B67 = vld1q_s8(b_group + 48);
                } else {
                /* k-group kg: k0-3, k-group kg+1 (at +32): k4-7 */
                const int8x16_t a0 = vld1q_s8(a_group);      /* rows 0-3 x k0-3 */
                const int8x16_t a1 = vld1q_s8(a_group + 16); /* rows 4-7 x k0-3 */
                const int8x16_t a2 = vld1q_s8(a_group + 32); /* rows 0-3 x k4-7 */
                const int8x16_t a3 = vld1q_s8(a_group + 48); /* rows 4-7 x k4-7 */
                A01 = zip_lo(a0, a2);        /* rows 0,1 x k0-7 */
                A23 = zip_hi(a0, a2);        /* rows 2,3 x k0-7 */
                A45 = zip_lo(a1, a3);        /* rows 4,5 x k0-7 */
                A67 = zip_hi(a1, a3);        /* rows 6,7 x k0-7 */

                const int8x16_t b0 = vld1q_s8(b_group);      /* cols 0-3 x k0-3 */
                const int8x16_t b1 = vld1q_s8(b_group + 16); /* cols 4-7 x k0-3 */
                const int8x16_t b2 = vld1q_s8(b_group + 32); /* cols 0-3 x k4-7 */
                const int8x16_t b3 = vld1q_s8(b_group + 48); /* cols 4-7 x k4-7 */
                B01 = zip_lo(b0, b2);        /* cols 0,1 x k0-7 */
                B23 = zip_hi(b0, b2);        /* cols 2,3 x k0-7 */
                B45 = zip_lo(b1, b3);        /* cols 4,5 x k0-7 */
                B67 = zip_hi(b1, b3);        /* cols 6,7 x k0-7 */

                }

                acc[0][0] = vmmlaq_s32(acc[0][0], A01, B01);
                acc[0][1] = vmmlaq_s32(acc[0][1], A01, B23);
                acc[0][2] = vmmlaq_s32(acc[0][2], A01, B45);
                acc[0][3] = vmmlaq_s32(acc[0][3], A01, B67);
                acc[1][0] = vmmlaq_s32(acc[1][0], A23, B01);
                acc[1][1] = vmmlaq_s32(acc[1][1], A23, B23);
                acc[1][2] = vmmlaq_s32(acc[1][2], A23, B45);
                acc[1][3] = vmmlaq_s32(acc[1][3], A23, B67);
                acc[2][0] = vmmlaq_s32(acc[2][0], A45, B01);
                acc[2][1] = vmmlaq_s32(acc[2][1], A45, B23);
                acc[2][2] = vmmlaq_s32(acc[2][2], A45, B45);
                acc[2][3] = vmmlaq_s32(acc[2][3], A45, B67);
                acc[3][0] = vmmlaq_s32(acc[3][0], A67, B01);
                acc[3][1] = vmmlaq_s32(acc[3][1], A67, B23);
                acc[3][2] = vmmlaq_s32(acc[3][2], A67, B45);
                acc[3][3] = vmmlaq_s32(acc[3][3], A67, B67);
            }
            if (xor_after_milestone) {
                uint32_t *out = tile_xor_out + static_cast<size_t>(ms) * tile_count + spatial_tile_id;
                const uint32_t x = xor_accumulators(acc);
                if (jg == 0) *out = x;
                else *out ^= x;
            }
        }
    }
}

bool case33_i8mm_packed_enabled() {
    static const bool enabled = [] { const char *v = std::getenv("CP_I8MM_PACKED"); return !(v && v[0] == '0'); }();
    return enabled;
}

// Each 64-byte unit contains two 4-K groups across eight rows/columns.
// All four loads precede stores; the permutation is safe in-place, with no
// extra matrix allocation. Reuse across thousands of GEMM tiles amortizes it.
void case33_i8mm_repack(int8_t *data, size_t bytes) {
#if defined(_OPENMP)
#pragma omp parallel for schedule(static)
#endif
    for (size_t i = 0; i < bytes / 64; ++i) {
        int8_t *p = data + i * 64;
        const auto a = vld1q_s8(p), b = vld1q_s8(p + 16);
        const auto c = vld1q_s8(p + 32), d = vld1q_s8(p + 48);
        vst1q_s8(p, zip_lo(a, c));
        vst1q_s8(p + 16, zip_hi(a, c));
        vst1q_s8(p + 32, zip_lo(b, d));
        vst1q_s8(p + 48, zip_hi(b, d));
    }
}

bool case33_i8mm_contiguous_b_enabled() {
    static const bool enabled = [] { const char *v = std::getenv("CP_I8MM_BCONTIG"); return !(v && v[0] == '0'); }();
    return case33_i8mm_packed_enabled() && enabled;
}

// Bring each 8-column K stream together. Only B (once per job) changes order;
// each worker needs one 16-column tile of scratch, never a full second matrix.
void case33_i8mm_repack_b(int8_t *data, size_t bytes, int blocks_k) {
    if (!case33_i8mm_contiguous_b_enabled() || blocks_k <= 0) return;
    const size_t tile_bytes = static_cast<size_t>(blocks_k) * kPanelB;
#if defined(_OPENMP)
#pragma omp parallel
#endif
    {
        std::vector<int8_t> scratch(tile_bytes);
#if defined(_OPENMP)
#pragma omp for schedule(static)
#endif
        for (size_t tile = 0; tile < bytes / tile_bytes; ++tile) {
            int8_t *p = data + tile * tile_bytes;
            constexpr int group_bytes = kPanelB / kColGroups;
            for (int jg = 0; jg < kColGroups; ++jg)
                for (int ms = 0; ms < blocks_k; ++ms)
                    std::memcpy(scratch.data() + (static_cast<size_t>(jg) * blocks_k + ms) * group_bytes,
                        p + static_cast<size_t>(ms) * kPanelB + jg * group_bytes, group_bytes);
            std::memcpy(p, scratch.data(), tile_bytes);
        }
    }
}

void case33_i8mm_micro_gemm_xor_fused_k(
        const int8_t *a, const int8_t *b, int blocks, int per_ms, int milestones,
        size_t tile, size_t count, bool fold, uint32_t *out) {
    if (case33_i8mm_packed_enabled()) {
        if (case33_i8mm_contiguous_b_enabled()) i8mm_kernel<true, true>(a, b, blocks, per_ms, milestones, tile, count, fold, out);
        else i8mm_kernel<true, false>(a, b, blocks, per_ms, milestones, tile, count, fold, out);
    } else i8mm_kernel<false>(a, b, blocks, per_ms, milestones, tile, count, fold, out);
}

#else
bool case33_i8mm_packed_enabled() { return false; }
bool case33_i8mm_contiguous_b_enabled() { return false; }
void case33_i8mm_repack_b(std::int8_t *, std::size_t, int) {}
void case33_i8mm_repack(std::int8_t *, std::size_t) {}
void case33_i8mm_micro_gemm_xor_fused_k(
        const std::int8_t *, const std::int8_t *, int, int, int, std::size_t, std::size_t, bool,
        std::uint32_t *) {}
#endif
