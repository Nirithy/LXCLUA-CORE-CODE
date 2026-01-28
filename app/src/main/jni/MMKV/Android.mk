
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := mmkv

LOCAL_SRC_FILES := luammkv.cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/MMKV \
    $(LOCAL_PATH)/../lua
LOCAL_CPPFLAGS := -std=c++17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing

LOCAL_LDLIBS += $(LOCAL_PATH)/libs/libcore.a -lz -lc++ -llog

LOCAL_STATIC_LIBRARIES := LXCLuaCore

include $(BUILD_SHARED_LIBRARY)
