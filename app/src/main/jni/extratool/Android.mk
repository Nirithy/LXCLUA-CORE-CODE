LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 模块名称
LOCAL_MODULE     := extratool
# 源文件
LOCAL_SRC_FILES  := extratool.c
# 包含Lua头文件
LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
# 编译标志
LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing

LOCAL_STATIC_LIBRARIES := LXCLuaCore

# 系统库链接
LOCAL_LDLIBS += -L$(SYSROOT)/usr/lib -llog -ldl -lz

include $(BUILD_SHARED_LIBRARY)