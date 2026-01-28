LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := physics
LOCAL_SRC_FILES  := physics.c
LOCAL_STATIC_LIBRARIES := LXCLuaCore
LOCAL_LDLIBS    := -lm -lz

LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing

include $(BUILD_SHARED_LIBRARY)
