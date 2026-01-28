LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE     := LuaBoost
LOCAL_SRC_FILES  := lua_boost.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../lua
LOCAL_CFLAGS := -std=c17 -O3 -flto \
                -funroll-loops -fomit-frame-pointer \
                -ffunction-sections -fdata-sections \
                -fstrict-aliasing

LOCAL_STATIC_LIBRARIES := LXCLuaCore

LOCAL_LDLIBS += -llog -ldl -lz
include $(BUILD_SHARED_LIBRARY)
