local _M = {}
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local PopupMenu = bindClass "androidx.appcompat.widget.PopupMenu"
local Intent = bindClass "android.content.Intent"
local SQLiteHelper = bindClass "com.difierline.lua.lxclua.utils.SQLiteHelper"(activity)
local AesUtil = bindClass "com.difierline.lua.lxclua.utils.AesUtil"
local OkHttpUtil = require "utils.OkHttpUtil"
local GlideUtil = require "utils.GlideUtil"
local ActivityUtil = require "utils.ActivityUtil"
local LuaRecyclerAdapter = require "utils.LuaRecyclerAdapter"
local MaterialBlurDialogBuilder = require "dialogs.MaterialBlurDialogBuilder"
local SharedPrefUtil = require "utils.SharedPrefUtil"
local Utils = require "utils.Utils"
local qq = require "qq"

-- 保留数据表作为适配器的数据源
local data = {}
local adapter_my

local STR_BOUND = res.string.bound
local STR_UNBOUND = res.string.unbound
local STR_CLICK_LOGIN = res.string.click_me_to_log_in

local function strToBool(str)
  return str and str ~= "" and str:lower() ~= "false"
end

local function initRecy()
  if not SharedPrefUtil.getBoolean("is_login") then return end

  if not adapter_my then
    -- 使用全局 data 表初始化适配器
    local adapter_my = LuaRecyclerAdapter(data, "layouts.my_item", {
      onBindViewHolder = function(viewHolder, pos, views, currentData)
        pcall(function()
          GlideUtil.set(activity.getLuaDir("res/drawable/" .. currentData.src .. ".png"), views.image)
        end)

        if currentData.text then
          if currentData.title == "qq" then
            views.content.setText(strToBool(currentData.text) and STR_BOUND or STR_UNBOUND)
           else
            views.content.setText(currentData.text)
          end
        end

        views.card.onClick = function()
          if currentData.title == "qq" and not strToBool(currentData.text) then
            qq.Login(102796665, function() end)
           elseif currentData.title == "book" then
            ActivityUtil.new("mypost")
           elseif currentData.title == "x_coins" then
            ActivityUtil.new("ranking")
          end
        end
      end
    })

    if recycler_view_my then
      recycler_view_my.setAdapter(adapter_my)
      recycler_view_my.setLayoutManager(LinearLayoutManager(activity))
    end
  end
end

function _M.getProfile()
  local function resetToLoginState()
    nick.setText(STR_CLICK_LOGIN)

    local UiUtil = require "utils.UiUtil"
    local defaultIconPath = UiUtil.isNightMode() and activity.getLuaDir("icon_night.png") or activity.getLuaDir("icon.png")
    GlideUtil.set(defaultIconPath, logo)
    title.parent.setVisibility(8)
    check.parent.setVisibility(8)
    email.setVisibility(8)
    recycler_view_my.parent.setVisibility(8)

    local corner = dp2px(12)
    if login then
      login.setBackgroundDrawable(createCornerGradientDrawable(
      true, Colors.colorBackground, Colors.colorOutlineVariant, corner, corner))
    end

    -- 清空数据表
    for i = #data, 1, -1 do
      table.remove(data, i)
    end

    -- 通知适配器更新
    if recycler_view_my.adapter then
      recycler_view_my.adapter.notifyDataSetChanged()
    end
  end

  local function updateProfileUI(profileData)
   -- MyToast("网络功能已移除")
  end

  local function stopRefreshing()
    if mSwipeRefreshLayout3 then
      mSwipeRefreshLayout3.setRefreshing(false)
    end
  end

  resetToLoginState()
  stopRefreshing()
end

local function initSwipeRefresh()
  mSwipeRefreshLayout3.setProgressViewOffset(true, -100, 250)
  mSwipeRefreshLayout3.setColorSchemeColors({ Colors.colorPrimary })
  mSwipeRefreshLayout3.setOnRefreshListener({
    onRefresh = function()
      _M.getProfile()
    end
  })
end

function _M.onCreate()
  if activity.getSharedData("offline_mode") then
    return
  end

  initRecy()
  _M.getProfile()
  initSwipeRefresh()

  function check.onClick()
   -- MyToast("网络功能已移除")
  end

  function login.onClick(v)
    MyToast("是的就是你！")
  end
end

function _M.onDestroy()
  if mSwipeRefreshLayout3 then
    mSwipeRefreshLayout3.setOnRefreshListener(nil)
  end

  if OkHttpUtil.cancelAllRequests then
    OkHttpUtil.cancelAllRequests()
  end

  if OkHttpUtil.cleanupDialogs then
    OkHttpUtil.cleanupDialogs()
  end

  -- 释放适配器
  recycler_view_my.adapter.release()
  recycler_view_my.adapter = nil

  return _M
end

return _M