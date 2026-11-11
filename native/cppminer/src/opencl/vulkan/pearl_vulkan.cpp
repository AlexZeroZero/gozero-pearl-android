#include "pearl_vulkan.hpp"
#include "pearl_spv.hpp"
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <vector>

namespace {
bool checked(VkResult r,int line){if(r==VK_SUCCESS)return true;std::fprintf(stderr,"[vulkan] API error %d at line %d\n",r,line);return false;}
#define VK_OK(call) do{if(!checked((call),__LINE__))return false;}while(0)
}
struct PearlVulkan::Impl {
    struct Buffer{VkBuffer handle=VK_NULL_HANDLE;VkDeviceMemory memory=VK_NULL_HANDLE;void*mapped=nullptr;size_t bytes=0;};
    VkInstance instance=VK_NULL_HANDLE;VkPhysicalDevice physical=VK_NULL_HANDLE;VkDevice device=VK_NULL_HANDLE;VkQueue queue=VK_NULL_HANDLE;
    VkDescriptorSetLayout setLayout=VK_NULL_HANDLE;VkPipelineLayout pipelineLayout=VK_NULL_HANDLE;VkDescriptorPool descriptorPool=VK_NULL_HANDLE;
    VkDescriptorSet set=VK_NULL_HANDLE;VkShaderModule module=VK_NULL_HANDLE;VkPipeline pipeline=VK_NULL_HANDLE;
    VkCommandPool commandPool=VK_NULL_HANDLE;VkCommandBuffer command=VK_NULL_HANDLE;VkFence fence=VK_NULL_HANDLE;
    Buffer buffers[3];int m=0,n=0,mm=0,nn=0;
    ~Impl(){
        if(device){
            // A hung submission exits this isolated worker rather than freeing live buffers.
            vkDeviceWaitIdle(device);
            if(fence)vkDestroyFence(device,fence,nullptr);if(commandPool)vkDestroyCommandPool(device,commandPool,nullptr);
            if(pipeline)vkDestroyPipeline(device,pipeline,nullptr);if(module)vkDestroyShaderModule(device,module,nullptr);
            if(descriptorPool)vkDestroyDescriptorPool(device,descriptorPool,nullptr);
            if(pipelineLayout)vkDestroyPipelineLayout(device,pipelineLayout,nullptr);if(setLayout)vkDestroyDescriptorSetLayout(device,setLayout,nullptr);
            for(auto&b:buffers){if(b.mapped)vkUnmapMemory(device,b.memory);if(b.handle)vkDestroyBuffer(device,b.handle,nullptr);if(b.memory)vkFreeMemory(device,b.memory,nullptr);}
            vkDestroyDevice(device,nullptr);
        }
        if(instance)vkDestroyInstance(instance,nullptr);
    }
    bool init(int rows,int cols,int macro_m,int macro_n){
        if(rows<=0||cols<=0||rows>65536||cols>65536||macro_m<=0||macro_n<=0||rows%macro_m||cols%macro_n)return false;
        m=rows;n=cols;mm=macro_m;nn=macro_n;
        const uint32_t*code=nullptr;size_t codeBytes=0;
        if(mm==64&&nn==64){code=pearl_vk_64_64;codeBytes=sizeof(pearl_vk_64_64);}
        else if(mm==128&&nn==128){code=pearl_vk_128_128;codeBytes=sizeof(pearl_vk_128_128);}
        else if(mm==64&&nn==256){code=pearl_vk_64_256;codeBytes=sizeof(pearl_vk_64_256);}else return false;
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};app.pApplicationName="Gozero Pearl INT8";app.apiVersion=VK_API_VERSION_1_3;
        VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ici.pApplicationInfo=&app;VK_OK(vkCreateInstance(&ici,nullptr,&instance));
        uint32_t count=0;VK_OK(vkEnumeratePhysicalDevices(instance,&count,nullptr));if(!count)return false;std::vector<VkPhysicalDevice>devices(count);VK_OK(vkEnumeratePhysicalDevices(instance,&count,devices.data()));
        for(auto p:devices){VkPhysicalDeviceProperties props;vkGetPhysicalDeviceProperties(p,&props);if(props.apiVersion<VK_API_VERSION_1_3)continue;
            VkPhysicalDeviceShaderIntegerDotProductFeatures f{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES};VkPhysicalDeviceFeatures2 features{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2};features.pNext=&f;vkGetPhysicalDeviceFeatures2(p,&features);
            VkPhysicalDeviceShaderIntegerDotProductProperties dp{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES};VkPhysicalDeviceProperties2 p2{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2};p2.pNext=&dp;vkGetPhysicalDeviceProperties2(p,&p2);
            if(f.shaderIntegerDotProduct&&dp.integerDotProduct4x8BitPackedSignedAccelerated&&props.limits.maxComputeWorkGroupInvocations>=uint32_t(mm*nn/32)){physical=p;break;}}
        if(!physical){std::fprintf(stderr,"[vulkan] No accelerated packed signed INT8 device\n");return false;}
        uint32_t qcount=0;vkGetPhysicalDeviceQueueFamilyProperties(physical,&qcount,nullptr);std::vector<VkQueueFamilyProperties>qprops(qcount);vkGetPhysicalDeviceQueueFamilyProperties(physical,&qcount,qprops.data());uint32_t family=0;while(family<qcount&&!(qprops[family].queueFlags&VK_QUEUE_COMPUTE_BIT))family++;if(family==qcount)return false;
        float priority=1;VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};qci.queueFamilyIndex=family;qci.queueCount=1;qci.pQueuePriorities=&priority;
        VkPhysicalDeviceShaderIntegerDotProductFeatures dot{VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES};dot.shaderIntegerDotProduct=VK_TRUE;
        VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};dci.pNext=&dot;dci.queueCreateInfoCount=1;dci.pQueueCreateInfos=&qci;VK_OK(vkCreateDevice(physical,&dci,nullptr,&device));vkGetDeviceQueue(device,family,0,&queue);
        VkPhysicalDeviceMemoryProperties mp;vkGetPhysicalDeviceMemoryProperties(physical,&mp);buffers[0].bytes=size_t(m)*4096;buffers[1].bytes=size_t(n)*4096;buffers[2].bytes=19*4;
        for(auto&b:buffers){VkBufferCreateInfo ci{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};ci.size=b.bytes;ci.usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;ci.sharingMode=VK_SHARING_MODE_EXCLUSIVE;VK_OK(vkCreateBuffer(device,&ci,nullptr,&b.handle));VkMemoryRequirements req;vkGetBufferMemoryRequirements(device,b.handle,&req);unsigned type=0;
            constexpr unsigned flags=VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            for(;type<mp.memoryTypeCount;type++)if((req.memoryTypeBits&(1u<<type))&&(mp.memoryTypes[type].propertyFlags&flags)==flags)break;if(type==mp.memoryTypeCount)return false;
            VkMemoryAllocateInfo ai{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};ai.allocationSize=req.size;ai.memoryTypeIndex=type;VK_OK(vkAllocateMemory(device,&ai,nullptr,&b.memory));VK_OK(vkBindBufferMemory(device,b.handle,b.memory,0));VK_OK(vkMapMemory(device,b.memory,0,b.bytes,0,&b.mapped));}
        VkDescriptorSetLayoutBinding bindings[3]{};for(int i=0;i<3;i++){bindings[i].binding=i;bindings[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;bindings[i].descriptorCount=1;bindings[i].stageFlags=VK_SHADER_STAGE_COMPUTE_BIT;}
        VkDescriptorSetLayoutCreateInfo slci{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};slci.bindingCount=3;slci.pBindings=bindings;VK_OK(vkCreateDescriptorSetLayout(device,&slci,nullptr,&setLayout));
        VkPushConstantRange range{VK_SHADER_STAGE_COMPUTE_BIT,0,12};VkPipelineLayoutCreateInfo plci{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};plci.setLayoutCount=1;plci.pSetLayouts=&setLayout;plci.pushConstantRangeCount=1;plci.pPushConstantRanges=&range;VK_OK(vkCreatePipelineLayout(device,&plci,nullptr,&pipelineLayout));
        VkDescriptorPoolSize ps{VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,3};VkDescriptorPoolCreateInfo dpci{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dpci.maxSets=1;dpci.poolSizeCount=1;dpci.pPoolSizes=&ps;VK_OK(vkCreateDescriptorPool(device,&dpci,nullptr,&descriptorPool));VkDescriptorSetAllocateInfo dsai{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};dsai.descriptorPool=descriptorPool;dsai.descriptorSetCount=1;dsai.pSetLayouts=&setLayout;VK_OK(vkAllocateDescriptorSets(device,&dsai,&set));
        VkDescriptorBufferInfo bi[3]{};VkWriteDescriptorSet writes[3]{};for(int i=0;i<3;i++){bi[i]={buffers[i].handle,0,buffers[i].bytes};writes[i]={VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET};writes[i].dstSet=set;writes[i].dstBinding=i;writes[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;writes[i].descriptorCount=1;writes[i].pBufferInfo=&bi[i];}vkUpdateDescriptorSets(device,3,writes,0,nullptr);
        VkShaderModuleCreateInfo smci{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};smci.codeSize=codeBytes;smci.pCode=code;VK_OK(vkCreateShaderModule(device,&smci,nullptr,&module));VkComputePipelineCreateInfo cpci{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};cpci.layout=pipelineLayout;cpci.stage={VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};cpci.stage.stage=VK_SHADER_STAGE_COMPUTE_BIT;cpci.stage.module=module;cpci.stage.pName="main";VK_OK(vkCreateComputePipelines(device,VK_NULL_HANDLE,1,&cpci,nullptr,&pipeline));
        VkCommandPoolCreateInfo cpc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};cpc.queueFamilyIndex=family;cpc.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;VK_OK(vkCreateCommandPool(device,&cpc,nullptr,&commandPool));VkCommandBufferAllocateInfo cbai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};cbai.commandPool=commandPool;cbai.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;cbai.commandBufferCount=1;VK_OK(vkAllocateCommandBuffers(device,&cbai,&command));VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};VK_OK(vkCreateFence(device,&fci,nullptr,&fence));
        std::printf("[vulkan] Packed signed INT8 scanner %dx%d, OpenCL noise and V3 witness retained\n",mm,nn);return true;
    }
    bool dispatch(uint32_t base,uint32_t count){
        VK_OK(vkResetCommandBuffer(command,0));VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};VK_OK(vkBeginCommandBuffer(command,&begin));
        VkMemoryBarrier host{VK_STRUCTURE_TYPE_MEMORY_BARRIER};host.srcAccessMask=VK_ACCESS_HOST_WRITE_BIT;host.dstAccessMask=VK_ACCESS_SHADER_READ_BIT|VK_ACCESS_SHADER_WRITE_BIT;vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,1,&host,0,nullptr,0,nullptr);
        vkCmdBindPipeline(command,VK_PIPELINE_BIND_POINT_COMPUTE,pipeline);vkCmdBindDescriptorSets(command,VK_PIPELINE_BIND_POINT_COMPUTE,pipelineLayout,0,1,&set,0,nullptr);uint32_t params[3]={uint32_t(m),uint32_t(n),base};vkCmdPushConstants(command,pipelineLayout,VK_SHADER_STAGE_COMPUTE_BIT,0,12,params);vkCmdDispatch(command,count,1,1);
        VkMemoryBarrier done{VK_STRUCTURE_TYPE_MEMORY_BARRIER};done.srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT;done.dstAccessMask=VK_ACCESS_HOST_READ_BIT|VK_ACCESS_HOST_WRITE_BIT;vkCmdPipelineBarrier(command,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,1,&done,0,nullptr,0,nullptr);VK_OK(vkEndCommandBuffer(command));
        VK_OK(vkResetFences(device,1,&fence));VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&command;VK_OK(vkQueueSubmit(queue,1,&submit,fence));
        VkResult waited=vkWaitForFences(device,1,&fence,VK_TRUE,10000000000ULL);
        if(waited==VK_TIMEOUT){std::fprintf(stderr,"[vulkan] Fence timeout; terminating isolated worker\n");std::fflush(stderr);std::_Exit(70);}
        VK_OK(waited);return true;
    }
};
PearlVulkan::PearlVulkan()=default;PearlVulkan::~PearlVulkan()=default;
bool PearlVulkan::prepare(int m,int n,int mm,int nn){
    if(impl_&&impl_->m==m&&impl_->n==n&&impl_->mm==mm&&impl_->nn==nn)return true;
    impl_.reset();auto next=std::make_unique<Impl>();if(!next->init(m,n,mm,nn))return false;impl_=std::move(next);return true;
}
void*PearlVulkan::a_data(){return impl_?impl_->buffers[0].mapped:nullptr;}void*PearlVulkan::b_data(){return impl_?impl_->buffers[1].mapped:nullptr;}
bool PearlVulkan::scan(const uint32_t key[8],const uint32_t bound[8],int batch,int*found,int*rows,int*cols,uint64_t*tiles,const std::function<bool()>&cancel,const std::function<void(uint64_t)>&progress){
    if(!impl_||!key||!bound)return false;auto&p=*impl_;auto*c=static_cast<uint32_t*>(p.buffers[2].mapped);std::memcpy(c,key,32);std::memcpy(c+8,bound,32);c[16]=0;c[17]=c[18]=UINT32_MAX;
    if(found)*found=0;if(rows)*rows=-1;if(cols)*cols=-1;if(tiles)*tiles=0;
    uint64_t scanned=0;int total=(p.m/p.mm)*(p.n/p.nn);batch=std::max(1,std::min(batch,256));
    for(int base=0;base<total;base+=batch){if(cancel&&cancel())return false;int count=std::min(batch,total-base);if(!p.dispatch(base,count))return false;scanned+=uint64_t(count)*p.mm*p.nn/32;if(tiles)*tiles=scanned;if(progress)progress(scanned);
        if(c[16]){if(c[17]>=uint32_t(p.m)||c[18]>=uint32_t(p.n)||c[17]%4||c[18]%8)return false;if(found)*found=1;if(rows)*rows=int(c[17]);if(cols)*cols=int(c[18]);break;}}
    return !(cancel&&cancel());
}
