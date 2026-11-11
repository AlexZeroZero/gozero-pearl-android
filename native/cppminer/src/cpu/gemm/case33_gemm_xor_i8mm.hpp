#pragma once

#include <cstddef>
#include <cstdint>

// Convert generic 4-K groups to SMMLA-ready 8-K pairs once per packed buffer.
// CP_I8MM_PACKED=0 retains the original layout/kernel for controlled comparisons.
bool case33_i8mm_packed_enabled();
// CP_I8MM_BCONTIG=0 restores the 0.2.0 interleaved milestone B layout.
bool case33_i8mm_contiguous_b_enabled();
void case33_i8mm_repack_b(std::int8_t *data, std::size_t bytes, int blocks_k);
void case33_i8mm_repack(std::int8_t *data, std::size_t bytes);

/* AArch64 I8MM (smmla) entry. The implementation is compiled in a dedicated
 * ARMv8.2+dotprod+i8mm translation unit and called only after runtime detection. */
void case33_i8mm_micro_gemm_xor_fused_k(
        const std::int8_t *a_base, const std::int8_t *b_base, int blocks_k,
        int blocks_per_milestone, int num_milestones, std::size_t spatial_tile_id,
        std::size_t tile_count, bool xor_after_milestone, std::uint32_t *tile_xor_out);
