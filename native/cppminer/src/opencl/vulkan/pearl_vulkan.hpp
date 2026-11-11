#pragma once
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>

// Optional Android scanner. OpenCL keeps generating the exact existing witness.
// The two APIs exchange mapped host buffers; all transfer time is included in scans.
class PearlVulkan {
public:
    PearlVulkan();
    ~PearlVulkan();
    PearlVulkan(const PearlVulkan&)=delete;
    PearlVulkan& operator=(const PearlVulkan&)=delete;
    bool prepare(int m,int n,int macro_m,int macro_n);
    void* a_data();void* b_data();
    bool scan(const uint32_t key[8],const uint32_t bound[8],int batch,
              int*found,int*rows,int*cols,uint64_t*tiles,
              const std::function<bool()>&cancel,const std::function<void(uint64_t)>&progress);
private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};
