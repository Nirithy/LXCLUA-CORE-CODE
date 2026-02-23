require "env"
setStatus()
local bindClass = luajava.bindClass
local LinearLayoutManager = bindClass "androidx.recyclerview.widget.LinearLayoutManager"
local ObjectAnimator = bindClass "android.animation.ObjectAnimator"
local RecyclerView = bindClass "androidx.recyclerview.widget.RecyclerView"
local LuaCustRecyclerHolder = bindClass "github.znzsofficial.adapter.LuaCustRecyclerHolder"
local PopupRecyclerAdapter = bindClass "github.znzsofficial.adapter.PopupRecyclerAdapter"
local Intent = bindClass "android.content.Intent"
local LinearLayoutCompat = bindClass "androidx.appcompat.widget.LinearLayoutCompat"
local Slider = bindClass "com.google.android.material.slider.Slider"
local DefaultItemAnimator = bindClass "androidx.recyclerview.widget.DefaultItemAnimator"
local LayoutAnimationController = bindClass "android.view.animation.LayoutAnimationController"
local AlphaAnimation = bindClass "android.view.animation.AlphaAnimation"
local ColorStateList = bindClass "android.content.res.ColorStateList"
local MaterialBlurDialogBuilder = require "dialogs.MaterialBlurDialogBuilder"
local GlideUtil = require "utils.GlideUtil"
local IconDrawable = require "utils.IconDrawable"
local SharedPrefUtil = require "utils.SharedPrefUtil"
local ActivityUtil = require "utils.ActivityUtil"
local Utils = require "utils.Utils"
user_id = tostring(tointeger(...))

local data_code = {}
local page_code = 1
local isLoading = false
local hasMore = true
local adapter_code = nil
local fadeInAnim = nil
local currentKeyword = ""
local colorError = Utils.setColorAlpha(Colors.colorError, 40)

activity
.setContentView(loadlayout("layouts.activity_privacy"))
.setSupportActionBar(toolbar)
.getSupportActionBar()
.setDisplayHomeAsUpEnabled(true)

-- 初始化RecyclerView
local function initRecycler()
  -- 设置动画
  local itemAnimator = DefaultItemAnimator()
  itemAnimator.setAddDuration(180)
  itemAnimator.setRemoveDuration(180)
  itemAnimator.setMoveDuration(180)
  itemAnimator.setChangeDuration(180)
  recycler_code.setItemAnimator(itemAnimator)

  local animation = AlphaAnimation(0, 1)
  animation.setDuration(180)
  local controller = LayoutAnimationController(animation)
  controller.setDelay(0.1)
  controller.setOrder(LayoutAnimationController.ORDER_NORMAL)
  recycler_code.setLayoutAnimation(controller)

  fadeInAnim = ObjectAnimator.ofFloat(recycler_code, "alpha", {0, 1})
  fadeInAnim.setDuration(180)

  -- 创建适配器
  adapter_code = PopupRecyclerAdapter(activity, PopupRecyclerAdapter.PopupCreator({
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
      views.time.setText(tostring(currentData.created_at))
      views.title.setText(tostring(currentData.title))
      views.title.getPaint().setFakeBoldText(true)
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

      function views.card.onClick(v)
     --   MyToast("网络功能已移除")
      end

      activity.onLongClick(views.card, function()
        local rotateAnim = ObjectAnimator.ofFloat(views.card, "rotation", {0, 5, -5, 0})
        rotateAnim.setDuration(300)
        rotateAnim.start()

        local items = {res.string.copy_header}
        local isAdmin = SharedPrefUtil.getBoolean("is_admin")
        local myUserId = SharedPrefUtil.getNumber("user_id")

        if isAdmin or myUserId == currentData.user_id then
          table.insert(items, res.string.delete_post)
          table.insert(items, res.string.modify_post)
          table.insert(items, res.string.off_the_shelf_post)

        end

        MaterialBlurDialogBuilder(activity)
        .setTitle(res.string.menu)
        .setItems(items, function(l, v)
          if items[v+1] == res.string.copy_header then
            activity.getSystemService("clipboard").setText(currentData.title)
           -- MyToast(res.string.copied_successfully)
           elseif items[v+1] == res.string.delete_post then
           -- MyToast("网络功能已移除")
           elseif items[v+1] == res.string.modify_post then
            --MyToast("网络功能已移除")
           elseif items[v+1] == res.string.off_the_shelf_post then
           -- MyToast("网络功能已移除")
          end
        end)
        .show()
        return true
      end)
    end
  }))

  recycler_code.setAdapter(adapter_code)
  recycler_code.setLayoutManager(LinearLayoutManager(activity))

  recycler_code.addItemDecoration(RecyclerView.ItemDecoration {
    getItemOffsets = function(outRect, view, parent, state)
      Utils.modifyItemOffsets(outRect, view, parent, adapter_code, 14)
    end
  })

end

-- 获取帖子数据（已移除网络功能）
local function getPosts(page, isRefresh)
  --MyToast("网络功能已移除")
end

local function refreshBannedChip()
  is_banned
  .setChipBackgroundColor(
  ColorStateList.valueOf(is_banned2 and colorError or 0))
  .setChipIcon(
  is_banned2 and IconDrawable("ic_account_off_outline", Colors.colorOnBackground)
  or IconDrawable("ic_account_outline", Colors.colorOnBackground))
end

-- 加载用户信息（已移除网络功能）
local function getProfile()
  --MyToast("网络功能已移除")
end

-- 刷新数据
function refreshData()
  page_code = 1
  hasMore = true
  getPosts(page_code, true)
end

-- 初始化下拉刷新
local function initSwipeRefresh()
  mSwipeRefreshLayout.setProgressViewOffset(true, -100, 200)
  mSwipeRefreshLayout.setColorSchemeColors({ Colors.colorPrimary })
  mSwipeRefreshLayout.setOnRefreshListener({
    onRefresh = function()
      refreshData()
      getProfile()
    end
  })
end

getProfile()

-- 初始化RecyclerView和下拉刷新
initRecycler()
initSwipeRefresh()

-- 首次加载数据
refreshData()

-- 滚动加载更多
recycler_code.addOnScrollListener(RecyclerView.OnScrollListener{
  onScrolled = function(recyclerView, dx, dy)
    if isLoading or not hasMore then return end

    local layoutManager = recyclerView.getLayoutManager()
    local visibleItemCount = layoutManager.getChildCount()
    local totalItemCount = layoutManager.getItemCount()
    local firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

    if (visibleItemCount + firstVisibleItemPosition) >= totalItemCount then
      page_code = page_code + 1
      getPosts(page_code, false)
    end
  end
})

function searchPosts(keywor)
  currentKeyword = keywor or ""
  refreshData()
end

function onCreateOptionsMenu(menu)
  --MyToast("网络功能已移除")
end

function onOptionsItemSelected(item)
  if item.getItemId() == android.R.id.home then
    activity.finish()
    return true
  end
end

function onDestroy()
  adapter_code.release()
  luajava.clear()
  collectgarbage("collect")
  collectgarbage("step")
end