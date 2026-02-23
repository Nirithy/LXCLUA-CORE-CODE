local _M = {}
local bindClass = luajava.bindClass
local packageInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0)
local Intent = bindClass "android.content.Intent"
local Uri = bindClass "android.net.Uri"
local MyBottomSheetDialog = require "dialogs.MyBottomSheetDialog"
local PathUtil = require "utils.PathUtil"

local versionName = packageInfo.versionName
local versionCode = packageInfo.versionCode

function _M.check()
  --MyToast("网络功能已移除")
   MyToast("当前版本为 " .. versionName .. "(" .. versionCode .. ")")
end

return _M