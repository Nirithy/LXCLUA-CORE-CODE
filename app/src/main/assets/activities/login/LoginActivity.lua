require "env"
setStatus()
local bindClass = luajava.bindClass
local Build = bindClass"android.os.Build"
local WindowManager = bindClass"android.view.WindowManager"
local View = bindClass"android.view.View"
local Color = bindClass"android.graphics.Color"
local InputType = bindClass"android.text.InputType"
local SQLiteHelper = bindClass "com.difierline.lua.lxclua.utils.SQLiteHelper"(activity)
local AesUtil = bindClass "com.difierline.lua.lxclua.utils.AesUtil"
local MaterialBlurDialogBuilder = require "dialogs.MaterialBlurDialogBuilder"
local Utils = require "utils.Utils"
local SharedPrefUtil = require "utils.SharedPrefUtil"
local IconDrawable = require "utils.IconDrawable"


-- 状态管理
local isRegisterMode = false
local forgotDialog = nil

-- 设置窗口属性
local window = activity.getWindow()
if Build.VERSION.SDK_INT >= 21 then
  window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
  window.getDecorView().setSystemUiVisibility(
  View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
  View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
  View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
  window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
  window.setStatusBarColor(Color.TRANSPARENT)
  window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
 else
  window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
  window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
end

-- 设置软键盘模式
activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

-- 加载布局
activity
.setContentView(loadlayout("layouts.activity_login"))
.setSupportActionBar(toolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)

title.getPaint().setFakeBoldText(true)

-- 通用网络请求处理（已移除网络功能）
local function handleNetworkRequest(url, params, successCallback)
 -- MyToast("网络功能已移除")
end

-- 界面模式切换
local function toggleUIMode()
  isRegisterMode = not isRegisterMode
  local visibility = isRegisterMode and View.VISIBLE or View.GONE
  email.setVisibility(visibility)

  login.setText(isRegisterMode and res.string.registration or res.string.login)
  register.setText(isRegisterMode and res.string.login or res.string.registration)
  title.setText(isRegisterMode and "Register" or "Login")
end

-- 登录按钮点击（已移除网络功能）
function login.onClick()
  MyToast("你就是最好的用户！")
end

-- 切换注册/登录模式
function register.onClick()
  toggleUIMode()
end

-- 忘记密码处理（已移除网络功能）
function forgot.onClick()
 -- MyToast("网络功能已移除")
end

-- 菜单项选择
function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
end

-- 创建菜单（已移除网络功能）
function onCreateOptionsMenu(menu)
 -- MyToast("网络功能已移除")
end

-- 清理资源
function onDestroy()
  luajava.clear()
  collectgarbage("collect") --全回收
  collectgarbage("step") -- 增量回收
end