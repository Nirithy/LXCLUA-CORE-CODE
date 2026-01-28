LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := network
LOCAL_SRC_FILES  := network.c
LOCAL_STATIC_LIBRARIES := lua
LOCAL_LDLIBS    := -lz -lm

LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing

include $(BUILD_SHARED_LIBRARY)
