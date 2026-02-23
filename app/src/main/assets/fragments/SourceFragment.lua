local _M = {}
local bindClass = luajava.bindClass
local ViewPager = bindClass "androidx.viewpager.widget.ViewPager"
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local ObjectAnimator = bindClass "android.animation.ObjectAnimator"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"
local LuaCustRecyclerHolder = bindClass "github.znzsofficial.adapter.LuaCustRecyclerHolder"
local PopupRecyclerAdapter = bindClass "github.znzsofficial.adapter.PopupRecyclerAdapter"
local ArrayAdapter = bindClass "android.widget.ArrayAdapter"
local SharedPrefUtil = require "utils.SharedPrefUtil"
local GlideUtil = require "utils.GlideUtil"
local Utils = require "utils.Utils"
local ActivityUtil = require "utils.ActivityUtil"
local MaterialBlurDialogBuilder = require "dialogs.MaterialBlurDialogBuilder"

-- 新增动画相关类绑定
local DefaultItemAnimator = bindClass "androidx.recyclerview.widget.DefaultItemAnimator"
local LayoutAnimationController = bindClass "android.view.animation.LayoutAnimationController"
local AlphaAnimation = bindClass "android.view.animation.AlphaAnimation"
local AnimationUtils = bindClass "android.view.animation.AnimationUtils"
local Android_R = bindClass "android.R"

local forumData = nil
-- 添加论坛数据映射表
local forumDataMap = {}
local data_code = {}
local id_code = 0
local page_code = 1
local isLoading = false
local hasMore = true
local isLoadingForums = false
local pendingGetCodeRequest = nil

-- 搜索状态变量
local isSearching = false
local searchKeyword = ""

-- 新增动画变量
local fadeInAnim = nil

-- 保存标签监听器以便临时移除
local tabListener = nil

local init_code = function()
  -- 初始化动画效果（缩短所有动画时长）
  local itemAnimator = DefaultItemAnimator()
  itemAnimator.setAddDuration(180) -- 减少70ms
  itemAnimator.setRemoveDuration(180) -- 减少70ms
  itemAnimator.setMoveDuration(180) -- 减少70ms
  itemAnimator.setChangeDuration(180) -- 减少70ms
  recycler_code.setItemAnimator(itemAnimator)

  -- 设置整体列表动画（缩短动画时长）
  local animation = AlphaAnimation(0, 1)
  animation.setDuration(180) -- 减少70ms
  local controller = LayoutAnimationController(animation)
  controller.setDelay(0.1) -- 减少50ms
  controller.setOrder(LayoutAnimationController.ORDER_NORMAL)
  recycler_code.setLayoutAnimation(controller)

  -- 创建渐入动画（缩短时长）
  fadeInAnim = ObjectAnimator.ofFloat(recycler_code, "alpha", {0, 1})
  fadeInAnim.setDuration(180) -- 减少70ms

  local adapter_code = PopupRecyclerAdapter(activity, PopupRecyclerAdapter.PopupCreator({
    getItemCount = function()
      return #data_code
    end,
    getItemViewType = function()
      return 0
    end,
    getPopupText = function(view, position)
      return ""
    end,
    onViewRecycled = function(holder)
    end,
    onCreateViewHolder = function(parent, viewType)
      local views = {}
      local holder = LuaCustRecyclerHolder(loadlayout("layouts.post_code_item", views))
      holder.Tag = views
      return holder
    end,
    onBindViewHolder = function(holder, position)
      local views = holder.Tag
      local currentData = data_code[position+1]
      local avatar = tostring(currentData.avatar_url)
      views.admin.parent.setVisibility(currentData.is_admin and 0 or 8)
      GlideUtil.set(activity.getLuaDir("res/drawable/default_avatar.png"), views.icon, true)
      views.nick.setText(tostring(currentData.nickname))
      xpcall(function()
        -- 使用映射表获取论坛名称
        local forumInfo = forumDataMap[currentData.forum_id]
        if forumInfo then
            views.time.setText(tostring(forumInfo.name) .. "  " .. tostring(currentData.created_at))
        else
            views.time.setText(tostring(currentData.created_at))
        end
        end, function()
        views.time.setText(tostring(currentData.created_at))
      end)
      views.title.setText(tostring(currentData.title))
      .getPaint().setFakeBoldText(true)
      views.content.setText(tostring(currentData.content))
      views.thumb.setText(tostring(tointeger(currentData.like_count)))
      views.view_count.setText(tostring(tointeger(currentData.view_count)))
      views.reply.setText(tostring(tointeger(currentData.comment_count)))
      views.star.setText(tostring(tointeger(currentData.favorite_count)))
      if currentData.price ~= 0 then
        views.price.parent.setVisibility(8)
        views.price.setText(currentData.purchased and res.string.purchased or tostring(tointeger(currentData.price)) .. " X币")
       else
        views.price.parent.setVisibility(8)
      end

      local function generateMenuItems()
        local items = {}
        local isAdmin = SharedPrefUtil.getBoolean("is_admin")
        local myUserId = SharedPrefUtil.getNumber("user_id")

        table.insert(items, res.string.copy_header)

        if isAdmin or myUserId == currentData.user_id then
          table.insert(items, res.string.delete_post)
          table.insert(items, res.string.modify_post)
          table.insert(items, res.string.off_the_shelf_post)
        end

        return items
      end

      function views.card.onClick()
     --   MyToast("网络功能已移除")
      end

      function views.icon.parent.parent.onClick()
        ActivityUtil.new("privacy", { currentData.user_id })
      end

      activity.onLongClick(views.card, function()
        -- 长按动画效果（缩短时长）
        local rotateAnim = ObjectAnimator.ofFloat(views.card, "rotation", {0, 5, -5, 0})
        rotateAnim.setDuration(300) -- 减少100ms
        rotateAnim.start()

        local item = generateMenuItems()
        local delete_post = tostring(res.string.delete_post)
        MaterialBlurDialogBuilder(activity)
        .setTitle(res.string.menu)
        .setItems(item, function(l, v)

          if item[v+1] == res.string.copy_header then
            activity.getSystemService("clipboard").setText(currentData.title)
            MyToast(res.string.copied_successfully)
           elseif item[v+1] == res.string.delete_post then
           -- MyToast("网络功能已移除")
           elseif item[v+1] == res.string.modify_post then
          --  MyToast("网络功能已移除")
           elseif item[v+1] == res.string.off_the_shelf_post then
           -- MyToast("网络功能已移除")
          end
        end)
        .show()

        return true
      end)
    end
  }))
  recycler_code.setAdapter(adapter_code).setLayoutManager(LinearLayoutManager(activity))
end

local function initSwipeRefresh()
  mSwipeRefreshLayout2.setProgressViewOffset(true, -100, 200) -- 减少50ms
  mSwipeRefreshLayout2.setColorSchemeColors({ Colors.colorPrimary })
  mSwipeRefreshLayout2.setOnRefreshListener({
    onRefresh = function()
      _M.refreshData()
    end
  })
end

function _M.refreshData()
  if not recycler_code.adapter then
    return
  end

  -- 重置搜索状态
  isSearching = false
  searchKeyword = ""

  page_code = 1
  hasMore = true
  local currentTabPosition = mtab_tag.getSelectedTabPosition()
  local currentTab = mtab_tag.getTabAt(currentTabPosition)
  local currentTag = currentTab and currentTab.getTag() or 0
  _M.getCode(currentTag, page_code, true)
end

function _M.getCode(id, page, isRefresh)
 -- MyToast("网络功能已移除")
end

-- 专用搜索加载函数（已移除网络功能）
function _M.loadSearchPage(keywor, page)
 -- MyToast("网络功能已移除")
end

function _M.search(newText)
  -- 设置搜索状态
  isSearching = true
  searchKeyword = newText and tostring(newText) or ""

  -- 重置分页状态
  page_code = 1
  hasMore = true

  -- 使用专用搜索函数
  _M.loadSearchPage(searchKeyword, page_code)
end

function _M.onCreate()
  if activity.getSharedData("offline_mode") then
    return
  end

  recycler_code.addItemDecoration(RecyclerView.ItemDecoration {
    getItemOffsets = function(outRect, view, parent, state)
      Utils.modifyItemOffsets(outRect, view, parent, recycler_code.adapter, 14)
    end
  })

  init_code()

  initSwipeRefresh()

  -- 创建标签监听器并保存
  tabListener = {
    onTabSelected = function(tab)
      -- 退出搜索状态
      isSearching = false
      searchKeyword = ""

      page_code = 1
      id_code = tab.getTag()
      hasMore = true
      _M.getCode(id_code, page_code, true)
    end,
    onTabUnselected = function(tab) end,
    onTabReselected = function(tab) end
  }
  mtab_tag.addOnTabSelectedListener(tabListener)

  -- 首次加载时添加延迟动画（保持延迟但缩短动画本身）
  recycler_code.alpha = 0
  local handler = luajava.bindClass("android.os.Handler")(luajava.bindClass("android.os.Looper").getMainLooper())
  handler.postDelayed(function()
    _M.getCode(0, 1, true)
  end, 250) -- 减少50ms

  recycler_code.addOnScrollListener(RecyclerView.OnScrollListener{
    onScrolled = function(recyclerView, dx, dy)
      if isLoading or not hasMore then return end

      local layoutManager = recyclerView.getLayoutManager()
      local visibleItemCount = layoutManager.getChildCount()
      local totalItemCount = layoutManager.getItemCount()
      local firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

      if (visibleItemCount + firstVisibleItemPosition) >= totalItemCount then
        mSwipeRefreshLayout2.setRefreshing(true)
        page_code = page_code + 1

        -- 根据搜索状态选择加载方式
        if isSearching then
          -- 带关键词的分页搜索
          _M.loadSearchPage(searchKeyword, page_code)
         else
          -- 普通分页加载
          _M.getCode(id_code, page_code, false)
        end
      end
    end
  })
end

function _M.onDestroy()
  -- 移除监听器和引用
  if mSwipeRefreshLayout2 then
    mSwipeRefreshLayout2.setOnRefreshListener(nil)
  end

  if mtab_tag and tabListener then
    mtab_tag.removeOnTabSelectedListener(tabListener)
    tabListener = nil
  end

  -- 释放大对象
  data_code = {}
  forumData = nil
  forumDataMap = {}
  recycler_code.adapter.release()
  recycler_code.adapter = nil

  -- 重置搜索状态
  isSearching = false
  searchKeyword = ""

  return _M
end

return _M
