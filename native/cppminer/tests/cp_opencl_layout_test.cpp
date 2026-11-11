#ifdef NDEBUG
#undef NDEBUG
#endif
#include "case32_layout.hpp"
#include <cassert>
#include <cstdio>
#include <vector>

int main() {
    assert(case32::configure(4,8,64,256));
    assert(case32::kMacroWorkItems==512);
    assert(case32::hash_tile_mr()==4 && case32::hash_tile_nr()==8);
    assert(case32::kMacroKgStripA==64*4 && case32::kMacroKgStripB==256*4);
    assert(case32::kMacroKbBlockA==64*128 && case32::kMacroKbBlockB==256*128);
    // The kernel swizzle must visit each macro exactly once, including tails
    // that take the linear mapping. Rectangular macros do not change coverage.
    for(int m:{1024,4096})for(int n:{1024,4096}) {
        const int rows=m/64,cols=n/256;
        std::vector<bool> seen(rows*cols);
        for(int mb=0;mb<rows*cols;mb++) {
            int im,jm;
            if(rows%8==0 && cols%4==0) {
                const int super_rows=rows/8,super=mb/32,within=mb%32;
                im=(super%super_rows)*8+within%8;
                jm=(super/super_rows)*4+within/8;
            } else {im=mb%rows;jm=mb/rows;}
            assert(im>=0 && im<rows && jm>=0 && jm<cols);
            const int index=jm*rows+im;assert(!seen[index]);seen[index]=true;
        }
        for(bool v:seen)assert(v);
        assert(rows*cols*case32::hash_tiles_per_macro()==(m/4)*(n/8));
    }
    assert(!case32::configure(4,8,256,256));
    assert(!case32::configure(4,8,64,512));
    assert(!case32::configure(8,4,64,256));
    assert(case32::configure(4,8,128,128) && case32::kMacroWorkItems==512);
    assert(case32::configure(8,8,64,64) && case32::kMacroWorkItems==64);
    puts("OpenCL layout coverage and bounds PASS");
}
