LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(LOCAL_PATH)/../lua
LOCAL_MODULE     := md5
LOCAL_SRC_FILES  := md5lib.c \
		md5.c \
		compat-5.2.c
LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing
LOCAL_STATIC_LIBRARIES := LXCLuaCore

include $(BUILD_SHARED_LIBRARY)

