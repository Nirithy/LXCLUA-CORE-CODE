require "import"
import "android.app.*"
import "android.os.*"
import "android.widget.*"
import "android.view.*"
import "android.graphics.*"
import "java.util.*"
import "android.content.*"
import "com.google.android.material.dialog.MaterialAlertDialogBuilder"
local config = {
  screen = {width = activity.getWidth() or 720, height = activity.getHeight() or 1280, fps = 1000},
  player = {width = 60, height = 80, speed = 8, color = 0xFF0000FF},
  bullet = {width = 10, height = 20, speed = 15, cooldown = 200, color = 0xFFFFFF00},
  enemy = {width = 50, height = 60, baseSpeed = 3, speedIncrement = 0.5, spawnInterval = 1000, color = 0xFFFF0000, health = 1, score = 10},
  explosion = {maxRadius = 30, duration = 300, color = 0xFFFFA500},
  game = {startLives = 3, startLevel = 1, levelUpScore = 100}
}
config.player.startX = config.screen.width / 2
config.player.startY = config.screen.height - 100
config.ui = {
  textSize = math.max(18, config.screen.width / 30),
  textColor = 0xFFFFFFFF,
  margin = math.max(15, config.screen.width / 36)
}
config.sound = {
  enabled = true,
  volume = 0.8
}
local SCREEN_WIDTH, SCREEN_HEIGHT, FPS = config.screen.width, config.screen.height, config.screen.fps
local GAME_SPEED = 1000 / FPS
local gameState = {state = "menu", running = false, paused = false, score = 0, lives = config.game.startLives, level = config.game.startLevel, difficulty = "normal", gameMode = "classic"}
local menuState = {selectedDifficulty = 1, selectedMode = 1, currentMenu = "main"}
local difficultySettings = {
  easy = {
    name = "简单",
    enemySpeedMultiplier = 0.7,
    enemySpawnMultiplier = 1.5,
    playerLives = 5
  },
  normal = {
    name = "普通",
    enemySpeedMultiplier = 1.0,
    enemySpawnMultiplier = 1.0,
    playerLives = 3
  },
  hard = {
    name = "困难",
    enemySpeedMultiplier = 1.5,
    enemySpawnMultiplier = 0.7,
    playerLives = 1
  }
}
local gameModeSettings = {
  classic = {
    name = "经典模式",
    description = "传统射击游戏"
  },
  survival = {
    name = "生存模式",
    description = "无限敌人，坚持到最后"
  },
  timeAttack = {
    name = "时间挑战",
    description = "60秒内获得最高分"
  }
}
local effectDatabase = {
  positive = {
    {name = "火力增强", description = "子弹伤害+50%", quality = "blue", type = "damage_boost", value = 1.5},
    {name = "快速射击", description = "射击冷却-40%", quality = "blue", type = "fire_rate_boost", value = 0.6},
    {name = "护盾强化", description = "获得额外生命+1", quality = "purple", type = "extra_life", value = 1},
    {name = "双重射击", description = "同时发射两发子弹", quality = "purple", type = "double_shot", value = true},
    {name = "无敌护盾", description = "10秒内免疫伤害", quality = "gold", type = "invincible", value = 10000}
  },
  negative = {
    {name = "武器过热", description = "射击冷却+50%，但子弹伤害+20%", quality = "gray", type = "fire_rate_debuff_with_damage", fireRateValue = 1.5, damageValue = 1.2},
    {name = "护盾失效", description = "失去1点生命，但移动速度+50%", quality = "red", type = "lose_life_with_speed", lifeValue = 1, speedValue = 1.5},
    {name = "敌人狂暴", description = "敌人速度+40%，但分数奖励+100%", quality = "red", type = "enemy_speed_boost_with_score", enemySpeedValue = 1.4, scoreValue = 2.0}
  }
}
local qualityColors = {
  blue = 0xFF4A90E2,
  purple = 0xFF9B59B6,
  gold = 0xFFF1C40F,
  gray = 0xFF95A5A6,
  red = 0xFFE74C3C
}
local playerEffects = {damageMultiplier = 1.0, fireRateMultiplier = 1.0, speedMultiplier = 1.0, scoreMultiplier = 1.0, enemySpeedMultiplier = 1.0, doubleShot = false, piercingShot = false, invincibleTime = 0, activeEffects = {}}
local levelUpPopup = {visible = false, options = {}, selectedOption = - 1, lastLevelCheck = 0}
local player = {x = SCREEN_WIDTH / 2 - config.player.width / 2, y = SCREEN_HEIGHT - 150, width = config.player.width, height = config.player.height, speed = config.player.speed, color = config.player.color}
local bullets, enemies, explosions, stars, bulletPool, enemyPool, explosionPool = {}, {}, {}, {}, {}, {}, {}
function getEnemyFromPool()
  if #enemyPool > 0 then
    return table.remove(enemyPool)
   else
    return {}
  end
end
function returnEnemyToPool(enemy)
  enemy.x = nil
  enemy.y = nil
  enemy.width = nil
  enemy.height = nil
  enemy.speed = nil
  enemy.type = nil
  enemy.score = nil
  enemy.health = nil
  table.insert(enemyPool, enemy)
end
for i = 1, 50 do
  table.insert(stars, {
    x = math.random(0, SCREEN_WIDTH),
    y = math.random(0, SCREEN_HEIGHT),
    size = math.random(1, 3),
    speed = math.random(1, 3),
    brightness = math.random(150, 255),
    twinkleSpeed = math.random(2, 3)
  })
end
local lastTime = System.currentTimeMillis()
local enemySpawnTimer = 0
local bulletCooldown = 0
local gameModeTimer = 0
local survivalWaveTimer = 0
local timeAttackDuration = 60000
local gameView = SurfaceView(activity)
local holder = gameView.getHolder()
local function gameLoop()
  local currentTime = System.currentTimeMillis()
  local deltaTime = currentTime - lastTime
  lastTime = currentTime
  if gameState.state == "playing" then
    updateGame(deltaTime)
    updateStars()
    renderGame()
   elseif gameState.state == "menu" then
    updateStars()
    renderMenu()
  end
  task(GAME_SPEED, gameLoop)
end
function updateGame(deltaTime)
  if gameState.paused then
    return
  end
  if playerEffects.invincibleTime > 0 then
    playerEffects.invincibleTime = math.max(0, playerEffects.invincibleTime - deltaTime)
  end
  gameModeTimer = gameModeTimer + deltaTime
  updateGameMode(deltaTime)
  if bulletCooldown > 0 then
    bulletCooldown = math.max(0, bulletCooldown - deltaTime)
  end
  enemySpawnTimer = enemySpawnTimer + deltaTime
  local spawnInterval = getEnemySpawnInterval()
  if enemySpawnTimer > spawnInterval then
    spawnEnemy()
    enemySpawnTimer = 0
  end
  if #bullets > 0 then
    updateBullets(deltaTime)
  end
  if #enemies > 0 then
    updateEnemies(deltaTime)
  end
  if #bullets > 0 and #enemies > 0 then
    checkCollisions()
  end
  if #explosions > 0 then
    cleanupObjects()
  end
end
function updateGameMode(deltaTime)
  if gameState.gameMode == "survival" then
    survivalWaveTimer = survivalWaveTimer + deltaTime
    if survivalWaveTimer > 10000 then
      survivalWaveTimer = 0
      gameState.level = gameState.level + 1
      if config.enemy.spawnInterval > 300 then
        config.enemy.spawnInterval = config.enemy.spawnInterval - 50
      end
    end
   elseif gameState.gameMode == "timeAttack" then
    if gameModeTimer >= timeAttackDuration then
      gameOver()
    end
   elseif gameState.gameMode == "classic" then
    local newLevel = math.floor(gameState.score / config.game.levelUpScore) + 1
    if newLevel > gameState.level then
      local levelDifference = newLevel - gameState.level
      gameState.level = newLevel
      checkLevelUpPopup(levelDifference)
    end
  end
end
function checkLevelUpPopup(levelDifference)
  if levelDifference == 1 or levelDifference == 3 or levelDifference == 5 then
    showLevelUpPopup()
  end
end
function showLevelUpPopup()
  gameState.paused = true
  levelUpPopup.visible = true
  local optionCount = math.random(2, 3)
  levelUpPopup.options = {}
  for i = 1, optionCount do
    local isPositive = math.random(1, 100) <= 70
    local effectList = isPositive and effectDatabase.positive or effectDatabase.negative
    local randomEffect = effectList[math.random(1, #effectList)]
    local option = {
      name = randomEffect.name,
      description = randomEffect.description,
      quality = randomEffect.quality,
      type = randomEffect.type,
      isPositive = isPositive
    }
    if randomEffect.value then
      option.value = randomEffect.value
    end
    if randomEffect.fireRateValue then
      option.fireRateValue = randomEffect.fireRateValue
    end
    if randomEffect.damageValue then
      option.damageValue = randomEffect.damageValue
    end
    if randomEffect.speedValue then
      option.speedValue = randomEffect.speedValue
    end
    if randomEffect.piercingValue then
      option.piercingValue = randomEffect.piercingValue
    end
    if randomEffect.lifeValue then
      option.lifeValue = randomEffect.lifeValue
    end
    if randomEffect.enemySpeedValue then
      option.enemySpeedValue = randomEffect.enemySpeedValue
    end
    if randomEffect.scoreValue then
      option.scoreValue = randomEffect.scoreValue
    end
    if randomEffect.removeBuff then
      option.removeBuff = randomEffect.removeBuff
    end
    if randomEffect.doubleShot then
      option.doubleShot = randomEffect.doubleShot
    end
    table.insert(levelUpPopup.options, option)
  end
end
function applySelectedEffect(optionIndex)
  if optionIndex < 1 or optionIndex > #levelUpPopup.options then return end
  local e = levelUpPopup.options[optionIndex]
  if e.type == "damage_boost" then playerEffects.damageMultiplier = playerEffects.damageMultiplier * e.value
   elseif e.type == "fire_rate_boost" then playerEffects.fireRateMultiplier = playerEffects.fireRateMultiplier * e.value
   elseif e.type == "extra_life" then gameState.lives = gameState.lives + e.value
   elseif e.type == "double_shot" then playerEffects.doubleShot = true
   elseif e.type == "invincible" then playerEffects.invincibleTime = e.value
   elseif e.type == "fire_rate_debuff_with_damage" then
    playerEffects.fireRateMultiplier = playerEffects.fireRateMultiplier * e.fireRateValue
    playerEffects.damageMultiplier = playerEffects.damageMultiplier * e.damageValue
   elseif e.type == "lose_life_with_speed" then
    gameState.lives = math.max(1, gameState.lives - e.lifeValue)
    playerEffects.speedMultiplier = playerEffects.speedMultiplier * e.speedValue
   elseif e.type == "enemy_speed_boost_with_score" then
    playerEffects.enemySpeedMultiplier = playerEffects.enemySpeedMultiplier * e.enemySpeedValue
    playerEffects.scoreMultiplier = playerEffects.scoreMultiplier * e.scoreValue
  end
  table.insert(playerEffects.activeEffects, {name = e.name, description = e.description, quality = e.quality})
  levelUpPopup.visible = false
  gameState.paused = false
end
function removeBuff()
  if playerEffects.damageMultiplier > 1.0 then
    playerEffects.damageMultiplier = 1.0
  end
  if playerEffects.fireRateMultiplier < 1.0 then
    playerEffects.fireRateMultiplier = 1.0
  end
  if playerEffects.speedMultiplier > 1.0 then
    playerEffects.speedMultiplier = 1.0
  end
  if playerEffects.scoreMultiplier > 1.0 then
    playerEffects.scoreMultiplier = 1.0
  end
  playerEffects.doubleShot = false
  playerEffects.piercingShot = false
  playerEffects.invincibleTime = 0
  for i = #playerEffects.activeEffects, 1, - 1 do
    local effect = playerEffects.activeEffects[i]
    if effect.quality == "blue" or effect.quality == "purple" or effect.quality == "gold" then
      table.remove(playerEffects.activeEffects, i)
    end
  end
end
function getEnemySpawnInterval()
  local baseInterval = config.enemy.spawnInterval
  local difficulty = difficultySettings[gameState.difficulty]
  if gameState.gameMode == "survival" then
    local timeReduction = math.floor(gameModeTimer / 5000) * 100
    return math.max(200, baseInterval * difficulty.enemySpawnMultiplier - timeReduction)
   elseif gameState.gameMode == "timeAttack" then
    return baseInterval * difficulty.enemySpawnMultiplier * 0.7
   else
    local levelMultiplier = math.max(0.3, 1 - (gameState.level - 1) * 0.1)
    return baseInterval * difficulty.enemySpawnMultiplier * levelMultiplier
  end
end
function spawnEnemy()
  local enemyType = determineEnemyType()
  local enemy = getEnemyFromPool()
  enemy.x = math.random(config.enemy.width, SCREEN_WIDTH - config.enemy.width)
  enemy.y = - config.enemy.height
  enemy.width = config.enemy.width
  enemy.height = config.enemy.height
  enemy.speed = config.enemy.baseSpeed + gameState.level * config.enemy.speedIncrement
  enemy.color = config.enemy.color
  enemy.health = enemyType.health
  enemy.type = enemyType.name
  enemy.score = enemyType.score
  table.insert(enemies, enemy)
end
function determineEnemyType()
  local level = gameState.level
  local random = math.random(1, 100)
  if level <= 2 then
    return {name = "light", health = 1, score = 10}
   elseif level <= 5 then
    if random <= 80 then
      return {name = "light", health = 1, score = 10}
     else
      return {name = "medium", health = 2, score = 25}
    end
   elseif level <= 10 then
    if random <= 50 then
      return {name = "light", health = 1, score = 10}
     elseif random <= 85 then
      return {name = "medium", health = 2, score = 25}
     else
      return {name = "heavy", health = 3, score = 50}
    end
   else
    if random <= 30 then
      return {name = "light", health = 1, score = 10}
     elseif random <= 70 then
      return {name = "medium", health = 2, score = 25}
     else
      return {name = "heavy", health = 3, score = 50}
    end
  end
end
function getEnemyInfoForLevel(level)
  if level <= 2 then
    return "敌人类型: 轻型 (10分)"
   elseif level <= 5 then
    return "敌人类型: 轻型(10分) + 中型(25分)"
   elseif level <= 10 then
    return "敌人类型: 轻型(10分) + 中型(25分) + 重型(50分)"
   else
    return "敌人类型: 全部类型 (重型增多)"
  end
end
function getBulletFromPool()
  if #bulletPool > 0 then
    return table.remove(bulletPool)
   else
    return {}
  end
end

function returnBulletToPool(bullet)
  table.insert(bulletPool, bullet)
end

function fireBullet()
  local adjustedCooldown = config.bullet.cooldown * playerEffects.fireRateMultiplier
  if bulletCooldown <= 0 then
    local bullet = getBulletFromPool()
    bullet.x = player.x + player.width / 2 - config.bullet.width / 2
    bullet.y = player.y
    bullet.width = config.bullet.width
    bullet.height = config.bullet.height
    bullet.speed = config.bullet.speed
    bullet.color = config.bullet.color
    bullet.piercing = playerEffects.piercingShot
    bullet.damage = playerEffects.damageMultiplier
    table.insert(bullets, bullet)
    if playerEffects.doubleShot then
      local bullet2 = getBulletFromPool()
      bullet2.x = player.x + player.width / 2 - config.bullet.width / 2 + 15
      bullet2.y = player.y
      bullet2.width = config.bullet.width
      bullet2.height = config.bullet.height
      bullet2.speed = config.bullet.speed
      bullet2.color = config.bullet.color
      bullet2.piercing = playerEffects.piercingShot
      bullet2.damage = playerEffects.damageMultiplier
      table.insert(bullets, bullet2)
    end
    bulletCooldown = adjustedCooldown
  end
end
function updateBullets(deltaTime)
  for i = #bullets, 1, - 1 do
    local bullet = bullets[i]
    bullet.y = bullet.y - bullet.speed
    if bullet.y < - bullet.height then
      returnBulletToPool(bullet)
      table.remove(bullets, i)
    end
  end
end
function updateEnemies(deltaTime)
  for i = #enemies, 1, - 1 do
    local enemy = enemies[i]
    local adjustedSpeed = enemy.speed * playerEffects.enemySpeedMultiplier
    enemy.y = enemy.y + adjustedSpeed
    if enemy.y > SCREEN_HEIGHT then
      returnEnemyToPool(enemy)
      table.remove(enemies, i)
      gameState.lives = gameState.lives - 1
      if gameState.lives <= 0 then
        gameOver()
      end
    end
  end
end
function checkCollisions()
  if #bullets == 0 or #enemies == 0 then
    return
  end

  for i = #bullets, 1, - 1 do
    local bullet = bullets[i]
    local bulletRemoved = false

    for j = #enemies, 1, - 1 do
      local enemy = enemies[j]
      if bullet.y > enemy.y + enemy.height or bullet.y + bullet.height < enemy.y then
        goto continue
      end

      if isColliding(bullet, enemy) then
        local explosionX = enemy.x + enemy.width / 2
        local explosionY = enemy.y + enemy.height / 2
        local explosionType = enemy.type
        local baseScore = enemy.score or config.enemy.score
        local finalScore = math.floor(baseScore * playerEffects.scoreMultiplier)
        gameState.score = gameState.score + finalScore
        returnEnemyToPool(enemy)
        table.remove(enemies, j)
        createExplosion(explosionX, explosionY, explosionType)
        if not bullet.piercing then
          returnBulletToPool(bullet)
          table.remove(bullets, i)
          bulletRemoved = true
          break
        end
      end
::continue::
    end
  end
  if playerEffects.invincibleTime <= 0 and #enemies > 0 then
    for i = #enemies, 1, - 1 do
      local enemy = enemies[i]
      if enemy.y > player.y + player.height or enemy.y + enemy.height < player.y then
        goto continue2
      end
      if isColliding(player, enemy) then
        local explosionX = enemy.x + enemy.width / 2
        local explosionY = enemy.y + enemy.height / 2
        local explosionType = enemy.type
        returnEnemyToPool(enemy)
        table.remove(enemies, i)
        gameState.lives = gameState.lives - 1
        createExplosion(explosionX, explosionY, explosionType)
        if gameState.lives <= 0 then
          gameOver()
        end
      end
::continue2::
    end
  end
end
function isColliding(obj1, obj2)
  return obj1.x < obj2.x + obj2.width and
  obj1.x + obj1.width > obj2.x and
  obj1.y < obj2.y + obj2.height and
  obj1.y + obj1.height > obj2.y
end
function createExplosion(x, y, enemyType)
  enemyType = enemyType or "light"
  local explosion = {
    x = x,
    y = y,
    radius = 5,
    maxRadius = config.explosion.maxRadius,
    life = config.explosion.duration,
    color = config.explosion.color,
    type = enemyType
  }
  if enemyType == "heavy" then
    explosion.maxRadius = config.explosion.maxRadius * 1.8
    explosion.life = config.explosion.duration * 1.5
    explosion.particleCount = 24
    explosion.shockWaves = 3
   elseif enemyType == "medium" then
    explosion.maxRadius = config.explosion.maxRadius * 1.3
    explosion.life = config.explosion.duration * 1.2
    explosion.particleCount = 16
    explosion.shockWaves = 2
   else
    explosion.maxRadius = config.explosion.maxRadius
    explosion.life = config.explosion.duration
    explosion.particleCount = 12
    explosion.shockWaves = 1
  end
  table.insert(explosions, explosion)
end
function cleanupObjects()
  for i = #explosions, 1, - 1 do
    local explosion = explosions[i]
    explosion.life = explosion.life - GAME_SPEED
    explosion.radius = explosion.radius + 1
    if explosion.life <= 0 or explosion.radius >= explosion.maxRadius then
      table.remove(explosions, i)
    end
  end
end
function updateStars()
  for i = 1, #stars do
    local star = stars[i]
    star.y = star.y + star.speed
    if star.y > SCREEN_HEIGHT then
      star.y = - 10
      star.x = math.random(0, SCREEN_WIDTH)
    end
  end
end
function drawStarField(canvas, paint)
  local time = System.currentTimeMillis() / 1000
  paint.setStyle(Paint.Style.FILL)
  for nebula = 1, 2 do
    local nebulaX = (SCREEN_WIDTH * nebula / 3) + math.sin(time * 0.05 + nebula) * 30
    local nebulaY = (SCREEN_HEIGHT * nebula / 4) + math.cos(time * 0.04 + nebula) * 20
    local nebulaAlpha = 25
    local nebulaRadius = 60
    local nebulaColors = {
      (nebulaAlpha << 24) | 0x00FF6600,
      (nebulaAlpha << 24) | 0x006600FF
    }
    paint.setColor(nebulaColors[nebula])
    canvas.drawCircle(nebulaX, nebulaY, nebulaRadius, paint)
  end
  for _, star in ipairs(stars) do
    local twinkleSpeed = star.twinkleSpeed or 2
    local twinkle = math.sin(time * twinkleSpeed) * 0.3 + 0.7
    local alpha = math.floor(star.brightness * twinkle)
    paint.setColor((alpha << 24) | 0x00FFFFFF)
    canvas.drawCircle(star.x, star.y, star.size, paint)
  end
end
function drawPlayer(canvas, paint, player)
  local centerX = player.x + player.width / 2
  paint.setStyle(Paint.Style.FILL)
  paint.setColor(0xFF0066AA)
  canvas.drawOval(player.x, player.y, player.x + player.width, player.y + player.height * 0.8, paint)
  paint.setColor(0xFF00CCFF)
  canvas.drawOval(player.x + player.width * 0.2, player.y + player.height * 0.1, player.x + player.width * 0.8, player.y + player.height * 0.6, paint)
  local time = System.currentTimeMillis() / 100
  local flameFlicker = math.sin(time) * 2
  paint.setColor(0xFFFF4400)
  canvas.drawOval(centerX - 8, player.y + player.height, centerX + 8, player.y + player.height + 15 + flameFlicker, paint)
  paint.setColor(0xFFFFAA00)
  canvas.drawCircle(centerX, player.y + player.height + 8, 3, paint)
end
function drawEnemy(canvas, paint, enemy)
  local centerX = enemy.x + enemy.width / 2
  local centerY = enemy.y + enemy.height / 2
  local radius = enemy.width / 2
  local enemyType = enemy.type or (enemy.health >= 3 and "heavy" or (enemy.health >= 2 and "medium" or "light"))
  paint.setStyle(Paint.Style.FILL)
  if enemyType == "heavy" then
    paint.setColor(0xFFFF3300)
    canvas.drawOval(centerX - radius, centerY - radius, centerX + radius, centerY + radius, paint)
    paint.setColor(0xFF990000)
    canvas.drawOval(centerX - radius / 2, centerY - radius / 2, centerX + radius / 2, centerY + radius / 2, paint)
   elseif enemyType == "medium" then
    paint.setColor(0xFF0088FF)
    canvas.drawOval(centerX - radius, centerY - radius, centerX + radius, centerY + radius, paint)
    paint.setColor(0xFF004488)
    canvas.drawOval(centerX - radius / 2, centerY - radius / 2, centerX + radius / 2, centerY + radius / 2, paint)
   else
    paint.setColor(0xFF666666)
    canvas.drawOval(centerX - radius, centerY - radius, centerX + radius, centerY + radius, paint)
    paint.setColor(0xFF333333)
    canvas.drawOval(centerX - radius / 2, centerY - radius / 2, centerX + radius / 2, centerY + radius / 2, paint)
  end
  if enemy.shield and enemy.shield > 0 then
    paint.setColor(0x8800FFFF)
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeWidth(3)
    canvas.drawCircle(centerX, centerY, radius + 5, paint)
    paint.setStyle(Paint.Style.FILL)
  end
end
function drawBullet(canvas, paint, bullet)
  local centerX = bullet.x + bullet.width / 2
  local centerY = bullet.y + bullet.height / 2
  local time = System.currentTimeMillis() / 100
  paint.setStyle(Paint.Style.FILL)
  for i = 1, 8 do
    local alpha = 255 - (i * 25)
    local trailY = centerY + (i * 12)
    local trailSize = bullet.width * (1 - i * 0.1)
    local trailFlicker = math.sin(time + i) * 0.2 + 0.8
    if i <= 3 then
      paint.setColor((math.floor(alpha * trailFlicker) << 24) | 0x00FFFF00)
     elseif i <= 6 then
      paint.setColor((math.floor(alpha * trailFlicker) << 24) | 0x00FF8800)
     else
      paint.setColor((math.floor(alpha * trailFlicker) << 24) | 0x00FF4400)
    end
    canvas.drawCircle(centerX, trailY, trailSize / 2, paint)
    if i % 2 == 0 then
      for j = 1, 3 do
        local particleX = centerX + (math.random() - 0.5) * trailSize
        local particleY = trailY + (math.random() - 0.5) * 4
        paint.setColor((math.floor(alpha * 0.5) << 24) | 0x00FFAA00)
        canvas.drawCircle(particleX, particleY, 0.5, paint)
      end
    end
  end
  local outerPulse = math.sin(time * 2) * 0.3 + 0.7
  paint.setColor((math.floor(80 * outerPulse) << 24) | 0x00FFFFFF)
  canvas.drawCircle(centerX, centerY, bullet.width / 2 + 6 + math.sin(time) * 2, paint)
  paint.setColor(0x99FFFF00)
  canvas.drawCircle(centerX, centerY, bullet.width / 2 + 4, paint)
  paint.setColor(0xAAFFAA00)
  canvas.drawCircle(centerX, centerY, bullet.width / 2 + 2, paint)
  paint.setColor(0xCCFFFFFF)
  canvas.drawCircle(centerX, centerY, bullet.width / 2 + 1, paint)
  paint.setColor(0xFFFFDD00)
  local bulletPath = Path()
  bulletPath.moveTo(centerX, bullet.y)
  bulletPath.lineTo(centerX + bullet.width / 3, bullet.y + bullet.height / 3)
  bulletPath.lineTo(centerX + bullet.width / 2, bullet.y + bullet.height * 0.8)
  bulletPath.lineTo(centerX, bullet.y + bullet.height)
  bulletPath.lineTo(centerX - bullet.width / 2, bullet.y + bullet.height * 0.8)
  bulletPath.lineTo(centerX - bullet.width / 3, bullet.y + bullet.height / 3)
  bulletPath.close()
  canvas.drawPath(bulletPath, paint)
  paint.setColor(0xFFFFFFFF)
  paint.setStyle(Paint.Style.STROKE)
  paint.setStrokeWidth(1)
  canvas.drawLine(centerX - bullet.width / 4, bullet.y + bullet.height / 4, centerX + bullet.width / 4, bullet.y + bullet.height / 4, paint)
  canvas.drawLine(centerX - bullet.width / 3, bullet.y + bullet.height / 2, centerX + bullet.width / 3, bullet.y + bullet.height / 2, paint)
  paint.setStyle(Paint.Style.FILL)
  local coreFlicker = math.sin(time * 3) * 0.4 + 0.6
  paint.setColor((math.floor(255 * coreFlicker) << 24) | 0x00FFFFFF)
  canvas.drawCircle(centerX, centerY, bullet.width / 3, paint)
  paint.setColor(0xFFFFFFFF)
  canvas.drawCircle(centerX, centerY - 1, bullet.width / 5, paint)
  paint.setStyle(Paint.Style.STROKE)
  paint.setStrokeWidth(1)
  local ringPulse = math.sin(time * 4) * 0.5 + 0.5
  paint.setColor((math.floor(150 * ringPulse) << 24) | 0x0000FFFF)
  canvas.drawCircle(centerX, centerY, bullet.width / 2 + 1, paint)
  paint.setStyle(Paint.Style.FILL)
  paint.setColor(0xFFFFFFFF)
  canvas.drawCircle(centerX, bullet.y + 2, 1.5, paint)
  paint.setColor(0xFF00FFFF)
  canvas.drawCircle(centerX, bullet.y + 2, 1, paint)
  for side = - 1, 1, 2 do
    for flow = 1, 4 do
      local flowY = bullet.y + bullet.height * (0.15 + flow * 0.2)
      local flowX = centerX + side * bullet.width / 3
      local flowFlicker = math.sin(time * 3 + flow + side) * 3
      local flowAlpha = math.floor(120 * (1 - flow / 5))
      paint.setColor((flowAlpha << 24) | 0x00FFFF00)
      canvas.drawCircle(flowX + flowFlicker, flowY, 1.5, paint)
      paint.setColor((flowAlpha << 24) | 0x00FFFFFF)
      canvas.drawCircle(flowX + flowFlicker, flowY, 0.8, paint)
    end
  end
  local shockAlpha = math.floor(80 * math.abs(math.sin(time * 6)))
  if shockAlpha > 20 then
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeWidth(2)
    paint.setColor((shockAlpha << 24) | 0x00FFFFFF)
    canvas.drawCircle(centerX, bullet.y + 3, bullet.width / 2 + 5, paint)
    paint.setStrokeWidth(1)
    paint.setColor((shockAlpha << 24) | 0x0000FFFF)
    canvas.drawCircle(centerX, bullet.y + 3, bullet.width / 2 + 3, paint)
  end
  paint.setStyle(Paint.Style.STROKE)
  for spiral = 1, 2 do
    local spiralTime = time * (3 + spiral) + spiral * math.pi
    local spiralRadius = bullet.width / 2 + 1 + spiral
    local spiralAlpha = math.floor(100 * (math.sin(spiralTime) * 0.5 + 0.5))
    paint.setStrokeWidth(1)
    paint.setColor((spiralAlpha << 24) | (spiral == 1 and 0x0000FFFF or 0x00FFFF00))
    canvas.drawCircle(centerX, centerY, spiralRadius, paint)
  end
  paint.setStyle(Paint.Style.FILL)
  for particle = 1, 6 do
    local particleAngle = particle * math.pi / 3 + time * 2
    local particleDistance = bullet.width / 2 + 3 + math.sin(time * 4 + particle) * 2
    local particleX = centerX + math.cos(particleAngle) * particleDistance
    local particleY = centerY + math.sin(particleAngle) * particleDistance
    local particleAlpha = math.floor(150 * (math.sin(time * 3 + particle) * 0.5 + 0.5))
    paint.setColor((particleAlpha << 24) | 0x00FFAA00)
    canvas.drawCircle(particleX, particleY, 1, paint)
  end
end
function drawExplosion(canvas, paint, explosion)
  local explosionType = explosion.type or "light"
  local maxDuration = explosionType == "heavy" and (config.explosion.duration * 1.5) or
  explosionType == "medium" and (config.explosion.duration * 1.2) or
  config.explosion.duration
  local progress = 1 - (explosion.life / maxDuration)
  local alpha = math.floor(255 * (1 - progress))
  local time = System.currentTimeMillis() / 100
  local colorTheme = {}
  if explosionType == "heavy" then
    colorTheme = {
      outer = 0x00AA0000,
      middle = 0x00FF2200,
      inner = 0x00FF6600,
      core = 0x00FFAA00
    }
   elseif explosionType == "medium" then
    colorTheme = {
      outer = 0x00FF4400,
      middle = 0x00FF6600,
      inner = 0x00FF8800,
      core = 0x00FFAA00
    }
   else
    colorTheme = {
      outer = 0x00FF6600,
      middle = 0x00FF8800,
      inner = 0x00FFAA00,
      core = 0x00FFDD00
    }
  end
  local particleCount = explosion.particleCount or 12
  local shockWaves = explosion.shockWaves or 1
  for layer = 1, 4 do
    local layerRadius = explosion.radius * (0.6 + layer * 0.5)
    local layerParticleCount = math.floor(particleCount * (0.5 + layer * 0.3))
    for i = 1, layerParticleCount do
      local angle = (i * 360 / layerParticleCount + layer * 20 + time * 0.5) * math.pi / 180
      local particleDistance = layerRadius * (0.6 + math.random() * 0.8)
      local particleX = explosion.x + math.cos(angle) * particleDistance
      local particleY = explosion.y + math.sin(angle) * particleDistance
      local particleSize = (5 - layer) * (1 - progress * 0.4) * (explosionType == "heavy" and 1.5 or explosionType == "medium" and 1.2 or 1)
      local particleAlpha = math.floor(alpha * (0.3 + math.random() * 0.5))
      if layer == 1 then
        paint.setColor((particleAlpha << 24) | colorTheme.outer)
       elseif layer == 2 then
        paint.setColor((particleAlpha << 24) | colorTheme.middle)
       elseif layer == 3 then
        paint.setColor((particleAlpha << 24) | colorTheme.inner)
       else
        paint.setColor((particleAlpha << 24) | colorTheme.core)
      end
      canvas.drawCircle(particleX, particleY, particleSize, paint)
      if explosionType == "heavy" and layer <= 2 and math.random() < 0.3 then
        local sparkX = particleX + (math.random() - 0.5) * 8
        local sparkY = particleY + (math.random() - 0.5) * 8
        paint.setColor((particleAlpha << 24) | 0x00FFFFFF)
        canvas.drawCircle(sparkX, sparkY, 1, paint)
      end
    end
  end
  paint.setStyle(Paint.Style.FILL)
  local mainRadius = explosion.radius * (explosionType == "heavy" and 1.3 or explosionType == "medium" and 1.1 or 1)
  paint.setColor((alpha << 24) | colorTheme.outer)
  canvas.drawCircle(explosion.x, explosion.y, mainRadius, paint)
  local midAlpha = math.floor(255 * (1 - progress * 0.6))
  paint.setColor((midAlpha << 24) | colorTheme.middle)
  canvas.drawCircle(explosion.x, explosion.y, mainRadius * 0.75, paint)
  local innerAlpha = math.floor(255 * (1 - progress * 0.4))
  paint.setColor((innerAlpha << 24) | colorTheme.inner)
  canvas.drawCircle(explosion.x, explosion.y, mainRadius * 0.5, paint)
  local coreAlpha = math.floor(255 * (1 - progress * 0.2))
  paint.setColor((coreAlpha << 24) | 0x00FFFFFF)
  canvas.drawCircle(explosion.x, explosion.y, mainRadius * 0.25, paint)
  for wave = 1, shockWaves do
    local waveProgress = progress + (wave - 1) * 0.15
    if waveProgress < 0.5 then
      local shockAlpha = math.floor(80 * (1 - waveProgress / 0.5) / wave)
      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeWidth(explosionType == "heavy" and 6 or explosionType == "medium" and 4 or 3)
      paint.setColor((shockAlpha << 24) | (wave == 1 and 0x00FFFFFF or colorTheme.core))
      local shockRadius = mainRadius * (1.2 + waveProgress * 0.8) * wave
      canvas.drawCircle(explosion.x, explosion.y, shockRadius, paint)
    end
  end
  paint.setStyle(Paint.Style.FILL)
  local sparkCount = explosionType == "heavy" and 24 or explosionType == "medium" and 18 or 12
  for i = 1, sparkCount do
    local sparkAngle = i * math.pi * 2 / sparkCount + progress * math.pi + time * 0.2
    local sparkDistance = mainRadius * (1.2 + math.random() * 0.6)
    local sparkX = explosion.x + math.cos(sparkAngle) * sparkDistance
    local sparkY = explosion.y + math.sin(sparkAngle) * sparkDistance
    local sparkAlpha = math.floor(alpha * (0.4 + math.random() * 0.4))
    local sparkSize = explosionType == "heavy" and 3 or explosionType == "medium" and 2.5 or 2
    if explosionType == "heavy" then
      paint.setColor((sparkAlpha << 24) | (i % 3 == 0 and 0x00FFFFFF or 0x00FFAA00))
     elseif explosionType == "medium" then
      paint.setColor((sparkAlpha << 24) | (i % 2 == 0 and 0x00FFFF00 or 0x00FFAA00))
     else
      paint.setColor((sparkAlpha << 24) | 0x00FFFF00)
    end
    canvas.drawCircle(sparkX, sparkY, sparkSize, paint)
    if explosionType == "heavy" and i % 4 == 0 then
      local trailX = sparkX - math.cos(sparkAngle) * 8
      local trailY = sparkY - math.sin(sparkAngle) * 8
      paint.setColor((math.floor(sparkAlpha * 0.6) << 24) | 0x00FF6600)
      canvas.drawCircle(trailX, trailY, 1.5, paint)
    end
  end
end
function renderMenu()
  local canvas = holder.lockCanvas()
  if canvas == nil then
    return
  end
  local paint = Paint()
  canvas.drawColor(0xFF000011)
  drawStarField(canvas, paint)
  if menuState.currentMenu == "main" then
    drawMainMenu(canvas, paint)
   elseif menuState.currentMenu == "difficulty" then
    drawDifficultyMenu(canvas, paint)
   elseif menuState.currentMenu == "mode" then
    drawModeMenu(canvas, paint)
  end
  holder.unlockCanvasAndPost(canvas)
end
function drawMainMenu(canvas, paint)
  local cx, cy = SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
  paint.setColor(0xFFFFFFFF); paint.setTextSize(60); paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText("星际射击", cx, cy - 150, paint)
  local w, h, s, y = 300, 60, 80, cy - 50
  drawMenuButton(canvas, paint, cx - w / 2, y, w, h, "开始游戏", 0xFF00AA00)
  drawMenuButton(canvas, paint, cx - w / 2, y + s, w, h, "难度: " .. difficultySettings[gameState.difficulty].name, 0xFFAA6600)
  drawMenuButton(canvas, paint, cx - w / 2, y + s * 2, w, h, "模式: " .. gameModeSettings[gameState.gameMode].name, 0xFF6600AA)
  drawMenuButton(canvas, paint, cx - w / 2, y + s * 3, w, h, "退出游戏", 0xFFAA0000)
  paint.setColor(0xFF888888); paint.setTextSize(18)
  canvas.drawText("点击按钮进行选择", cx, SCREEN_HEIGHT - 50, paint)
end
function drawDifficultyMenu(canvas, paint)
  local cx, cy = SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
  paint.setColor(0xFFFFFFFF); paint.setTextSize(48); paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText("选择难度", cx, cy - 200, paint)
  local w, h, s, y = 350, 80, 100, cy - 100
  drawMenuButton(canvas, paint, cx - w / 2, y, w, h, "简单(5条生命)", menuState.selectedDifficulty == 1 and 0xFF00FF00 or 0xFF006600)
  drawMenuButton(canvas, paint, cx - w / 2, y + s, w, h, "普通(3条生命)", menuState.selectedDifficulty == 2 and 0xFFFFAA00 or 0xFF664400)
  drawMenuButton(canvas, paint, cx - w / 2, y + s * 2, w, h, "困难 (1条生命)", menuState.selectedDifficulty == 3 and 0xFFFF0000 or 0xFF660000)
  drawMenuButton(canvas, paint, cx - 150, y + s * 3 + 20, 300, 50, "返回", 0xFF666666)
end
function drawModeMenu(canvas, paint)
  local cx, cy = SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
  paint.setColor(0xFFFFFFFF); paint.setTextSize(48); paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText("选择模式", cx, cy - 200, paint)
  local w, h, s, y = 350, 80, 130, cy - 100
  drawMenuButton(canvas, paint, cx - w / 2, y, w, h, "经典模式", menuState.selectedMode == 1 and 0xFF0099FF or 0xFF004488)
  paint.setColor(0xFF888888); paint.setTextSize(16)
  canvas.drawText("传统射击游戏", cx, y + h + 20, paint)
  drawMenuButton(canvas, paint, cx - w / 2, y + s, w, h, "生存模式", menuState.selectedMode == 2 and 0xFFAA00AA or 0xFF440044)
  canvas.drawText("无限敌人，坚持到最后", cx, y + s + h + 20, paint)
  drawMenuButton(canvas, paint, cx - w / 2, y + s * 2, w, h, "时间挑战", menuState.selectedMode == 3 and 0xFFFF6600 or 0xFF663300)
  canvas.drawText("60秒内获得最高分", cx, y + s * 2 + h + 20, paint)
  drawMenuButton(canvas, paint, cx - 150, y + s * 3 + 20, 300, 50, "返回", 0xFF666666)
end
function drawMenuButton(canvas, paint, x, y, width, height, text, color)
  paint.setColor(color)
  paint.setStyle(Paint.Style.FILL)
  canvas.drawRoundRect(x, y, x + width, y + height, 15, 15, paint)
  paint.setColor(0xFFFFFFFF)
  paint.setStyle(Paint.Style.STROKE)
  paint.setStrokeWidth(3)
  canvas.drawRoundRect(x, y, x + width, y + height, 15, 15, paint)
  paint.setColor(0xFFFFFFFF)
  paint.setStyle(Paint.Style.FILL)
  paint.setTextSize(24)
  paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText(text, x + width / 2, y + height / 2 + 8, paint)
end
function renderGame()
  local canvas = holder.lockCanvas()
  if canvas then
    local gradient = LinearGradient(0, 0, 0, SCREEN_HEIGHT, 0xFF000033, 0xFF000000, Shader.TileMode.CLAMP)
    local paint = Paint()
    paint.setShader(gradient)
    canvas.drawRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, paint)
    paint.setShader(nil)
    drawStarField(canvas, paint)
    drawPlayer(canvas, paint, player)
    for _, bullet in ipairs(bullets) do
      drawBullet(canvas, paint, bullet)
    end
    for _, enemy in ipairs(enemies) do
      drawEnemy(canvas, paint, enemy)
    end
    for _, explosion in ipairs(explosions) do
      drawExplosion(canvas, paint, explosion)
    end
    local panelWidth = SCREEN_WIDTH * 0.45
    local panelHeight = SCREEN_HEIGHT * 0.18
    local panelGradient = LinearGradient(config.ui.margin, config.ui.margin, config.ui.margin, panelHeight, 0xAA001122, 0xAA000000, Shader.TileMode.CLAMP)
    paint.setShader(panelGradient)
    paint.setStyle(Paint.Style.FILL)
    canvas.drawRoundRect(config.ui.margin, config.ui.margin, panelWidth, panelHeight, 20, 20, paint)
    paint.setShader(nil)
    paint.setColor(0xFF00CCFF)
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeWidth(4)
    canvas.drawRoundRect(config.ui.margin, config.ui.margin, panelWidth, panelHeight, 20, 20, paint)
    paint.setColor(0xFF0088CC)
    paint.setStrokeWidth(2)
    canvas.drawRoundRect(config.ui.margin + 3, config.ui.margin + 3, panelWidth - 3, panelHeight - 3, 17, 17, paint)
    paint.setStyle(Paint.Style.FILL)
    local textY1 = config.ui.margin + config.ui.textSize + 15
    paint.setColor(0xFFFFDD00)
    paint.setTextSize(config.ui.textSize)
    canvas.drawText("分数: " .. gameState.score, config.ui.margin + 8, textY1, paint)
    local textY2 = textY1 + config.ui.textSize + 20
    if gameState.lives > 2 then
      paint.setColor(0xFF00FF00)
     elseif gameState.lives > 1 then
      paint.setColor(0xFFFFAA00)
     else
      paint.setColor(0xFFFF0000)
    end
    paint.setTextSize(config.ui.textSize)
    canvas.drawText("生命: " .. gameState.lives, config.ui.margin + 8, textY2, paint)
    local textY3 = textY2 + config.ui.textSize + 20
    paint.setColor(0xFF00DDFF)
    paint.setTextSize(config.ui.textSize)
    canvas.drawText("等级: " .. gameState.level, config.ui.margin + 8, textY3, paint)
    local textY4 = textY3 + config.ui.textSize + 20
    if gameState.gameMode == "timeAttack" then
      local remainingTime = math.max(0, timeAttackDuration - gameModeTimer)
      local seconds = math.ceil(remainingTime / 1000)
      paint.setColor(seconds <= 10 and 0xFFFF0000 or 0xFFFFAA00)
      paint.setTextSize(config.ui.textSize)
      canvas.drawText("时间: " .. seconds .. "s", config.ui.margin + 8, textY4, paint)
     elseif gameState.gameMode == "survival" then
      local survivalTime = math.floor(gameModeTimer / 1000)
      paint.setColor(0xFF00FF88)
      paint.setTextSize(config.ui.textSize)
      canvas.drawText("存活: " .. survivalTime .. "s", config.ui.margin + 8, textY4, paint)
      local textY5 = textY4 + config.ui.textSize + 15
      local wave = math.floor(gameModeTimer / 10000) + 1
      paint.setColor(0xFFFF8800)
      canvas.drawText("波次: " .. wave, config.ui.margin + 8, textY5, paint)
     elseif gameState.gameMode == "classic" then
      local progress = gameState.score % config.game.levelUpScore
      local nextLevelScore = config.game.levelUpScore - progress
      paint.setColor(0xFF888888)
      paint.setTextSize(config.ui.textSize - 4)
      canvas.drawText("升级还需: " .. nextLevelScore, config.ui.margin + 8, textY4, paint)
      local textY5 = textY4 + config.ui.textSize + 10
      local enemyInfo = getEnemyInfoForLevel(gameState.level)
      paint.setColor(0xFF00CCAA)
      paint.setTextSize(config.ui.textSize - 6)
      canvas.drawText(enemyInfo, config.ui.margin + 8, textY5, paint)
    end
    paint.setColor(0xFF004488)
    canvas.drawRoundRect(config.ui.margin + 8, textY3 + 15, panelWidth - 16, textY3 + 25, 5, 5, paint)
    local energyProgress = (gameState.score % 100) / 100
    local energyWidth = (panelWidth - 32) * energyProgress
    paint.setColor(0xFF00AAFF)
    canvas.drawRoundRect(config.ui.margin + 12, textY3 + 17, config.ui.margin + 12 + energyWidth, textY3 + 23, 3, 3, paint)
    if energyProgress > 0.8 then
      local time = System.currentTimeMillis() / 200
      local flicker = math.sin(time) * 0.3 + 0.7
      paint.setColor((math.floor(255 * flicker) << 24) | 0x00FFFFFF)
      canvas.drawRoundRect(config.ui.margin + 12, textY3 + 17, config.ui.margin + 12 + energyWidth, textY3 + 23, 3, 3, paint)
    end
    if levelUpPopup.visible then
      drawLevelUpPopup(canvas, paint)
    end
    holder.unlockCanvasAndPost(canvas)
  end
end
function drawLevelUpPopup(canvas, paint)
  local centerX = SCREEN_WIDTH / 2
  local centerY = SCREEN_HEIGHT / 2
  paint.setColor(0x88000000)
  paint.setStyle(Paint.Style.FILL)
  canvas.drawRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, paint)
  local popupWidth = SCREEN_WIDTH * 0.85
  local popupHeight = SCREEN_HEIGHT * 0.6
  local popupX = centerX - popupWidth / 2
  local popupY = centerY - popupHeight / 2
  local gradient = LinearGradient(popupX, popupY, popupX, popupY + popupHeight,
  0xFF1A1A2E, 0xFF16213E, Shader.TileMode.CLAMP)
  paint.setShader(gradient)
  canvas.drawRoundRect(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 25, 25, paint)
  paint.setShader(nil)
  paint.setColor(0xFF00DDFF)
  paint.setStyle(Paint.Style.STROKE)
  paint.setStrokeWidth(4)
  canvas.drawRoundRect(popupX, popupY, popupX + popupWidth, popupY + popupHeight, 25, 25, paint)
  paint.setColor(0xFF0088CC)
  paint.setStrokeWidth(2)
  canvas.drawRoundRect(popupX + 3, popupY + 3, popupX + popupWidth - 3, popupY + popupHeight - 3, 22, 22, paint)
  paint.setStyle(Paint.Style.FILL)
  paint.setColor(0xFFFFDD00)
  paint.setTextSize(36)
  paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText("等级提升", centerX, popupY + 60, paint)
  paint.setColor(0xFFCCCCCC)
  paint.setTextSize(20)
  canvas.drawText("选择一个增益或减益效果", centerX, popupY + 90, paint)
  local optionCount = #levelUpPopup.options
  local optionHeight = 80
  local optionSpacing = 20
  local startY = popupY + 130
  for i = 1, optionCount do
    local option = levelUpPopup.options[i]
    local optionY = startY + (i - 1) * (optionHeight + optionSpacing)
    local optionX = popupX + 20
    local optionWidth = popupWidth - 40
    local qualityColor = qualityColors[option.quality] or 0xFF666666
    local optionGradient = LinearGradient(optionX, optionY, optionX, optionY + optionHeight,
    qualityColor, qualityColor & 0x88FFFFFF, Shader.TileMode.CLAMP)
    paint.setShader(optionGradient)
    paint.setStyle(Paint.Style.FILL)
    canvas.drawRoundRect(optionX, optionY, optionX + optionWidth, optionY + optionHeight, 15, 15, paint)
    paint.setShader(nil)
    paint.setColor(qualityColor)
    paint.setStyle(Paint.Style.STROKE)
    paint.setStrokeWidth(3)
    canvas.drawRoundRect(optionX, optionY, optionX + optionWidth, optionY + optionHeight, 15, 15, paint)
    paint.setStyle(Paint.Style.FILL)
    paint.setColor(qualityColor)
    canvas.drawRoundRect(optionX + 5, optionY + 5, optionX + 15, optionY + optionHeight - 5, 5, 5, paint)
    paint.setColor(0xFFFFFFFF)
    paint.setTextSize(24)
    paint.setTextAlign(Paint.Align.LEFT)
    canvas.drawText(option.name, optionX + 25, optionY + 30, paint)
    paint.setColor(0xFFCCCCCC)
    paint.setTextSize(18)
    canvas.drawText(option.description, optionX + 25, optionY + 55, paint)
    local qualityText = ""
    if option.quality == "blue" then
      qualityText = "[普通]"
     elseif option.quality == "purple" then
      qualityText = "[稀有]"
     elseif option.quality == "gold" then
      qualityText = "[传说]"
     elseif option.quality == "gray" then
      qualityText = "[负面]"
     elseif option.quality == "red" then
      qualityText = "[危险]"
    end
    paint.setColor(qualityColor)
    paint.setTextSize(16)
    paint.setTextAlign(Paint.Align.RIGHT)
    canvas.drawText(qualityText, optionX + optionWidth - 10, optionY + 25, paint)
    paint.setColor(0xFF888888)
    paint.setTextSize(20)
    paint.setTextAlign(Paint.Align.CENTER)
    canvas.drawText(tostring(i), optionX + optionWidth - 30, optionY + 50, paint)
  end
  local skipButtonWidth = 120
  local skipButtonHeight = 40
  local skipButtonX = centerX - skipButtonWidth / 2
  local skipButtonY = popupY + popupHeight - 70
  paint.setColor(0xCC666666)
  paint.setStyle(Paint.Style.FILL)
  canvas.drawRoundRect(skipButtonX, skipButtonY, skipButtonX + skipButtonWidth, skipButtonY + skipButtonHeight, 8, 8, paint)
  paint.setColor(0xFF999999)
  paint.setStyle(Paint.Style.STROKE)
  paint.setStrokeWidth(2)
  canvas.drawRoundRect(skipButtonX, skipButtonY, skipButtonX + skipButtonWidth, skipButtonY + skipButtonHeight, 8, 8, paint)
  paint.setStyle(Paint.Style.FILL)
  paint.setColor(0xFFFFFFFF)
  paint.setTextSize(20)
  paint.setTextAlign(Paint.Align.CENTER)
  canvas.drawText("跳过", centerX, skipButtonY + 26, paint)
  paint.setColor(0xFF888888)
  paint.setTextSize(16)
  canvas.drawText("点击选项进行选择或跳过", centerX, popupY + popupHeight - 20, paint)
end
function gameOver()
  gameState.running = false
  gameState.state = "menu"
  MaterialAlertDialogBuilder(activity)
  .setTitle("游戏结束")
  .setMessage("最终分数: " .. gameState.score .. "\n难度: " .. difficultySettings[gameState.difficulty].name .. "\n模式: " .. gameModeSettings[gameState.gameMode].name)
  .setPositiveButton("返回菜单", DialogInterface.OnClickListener{
    onClick = function()
      returnToMenu()
    end
  })
  .setNegativeButton("退出", DialogInterface.OnClickListener{
    onClick = function()
      activity.finish()
    end
  })
  .show()
end
function returnToMenu()
  gameState.state = "menu"
  gameState.running = false
  menuState.currentMenu = "main"
  bullets = {}
  enemies = {}
  explosions = {}
  enemySpawnTimer = 0
  bulletCooldown = 0
  gameModeTimer = 0
  survivalWaveTimer = 0
  playerEffects.damageMultiplier = 1.0
  playerEffects.fireRateMultiplier = 1.0
  playerEffects.speedMultiplier = 1.0
  playerEffects.scoreMultiplier = 1.0
  playerEffects.enemySpeedMultiplier = 1.0
  playerEffects.doubleShot = false
  playerEffects.piercingShot = false
  playerEffects.invincibleTime = 0
  playerEffects.activeEffects = {}
  levelUpPopup.visible = false
  levelUpPopup.options = {}
  lastTime = System.currentTimeMillis()
end
local function updatePlayerPosition(x, y)
  player.x = math.max(0, math.min(x - player.width / 2, SCREEN_WIDTH - player.width))
  player.y = math.max(0, math.min(y - player.height / 2, SCREEN_HEIGHT - player.height))
end
gameView.setOnTouchListener(View.OnTouchListener{
  onTouch = function(view, event)
    local action, x, y = event.getAction(), event.getX(), event.getY()
    if action == MotionEvent.ACTION_DOWN then
      if gameState.state == "menu" then handleMenuTouch(x, y)
       elseif gameState.state == "playing" then
        if levelUpPopup.visible then handleLevelUpPopupTouch(x, y)
         else updatePlayerPosition(x, y); fireBullet() end
      end
     elseif action == MotionEvent.ACTION_MOVE and gameState.state == "playing" and not levelUpPopup.visible then
      updatePlayerPosition(x, y); fireBullet()
    end
    return true
  end
})
function handleMenuTouch(x, y)
  local cx, cy = SCREEN_WIDTH / 2, SCREEN_HEIGHT / 2
  local function checkButton(bx, by, bw, bh) return isPointInRect(x, y, bx, by, bw, bh) end
  if menuState.currentMenu == "main" then
    local w, h, s, sy = 300, 60, 80, cy - 50
    if checkButton(cx - w / 2, sy, w, h) then startGame()
     elseif checkButton(cx - w / 2, sy + s, w, h) then menuState.currentMenu = "difficulty"
     elseif checkButton(cx - w / 2, sy + s * 2, w, h) then menuState.currentMenu = "mode"
     elseif checkButton(cx - w / 2, sy + s * 3, w, h) then activity.finish() end
   elseif menuState.currentMenu == "difficulty" then
    local w, h, s, ey = 350, 80, 100, cy - 100
    if checkButton(cx - w / 2, ey, w, h) then menuState.selectedDifficulty, gameState.difficulty = 1, "easy"
     elseif checkButton(cx - w / 2, ey + s, w, h) then menuState.selectedDifficulty, gameState.difficulty = 2, "normal"
     elseif checkButton(cx - w / 2, ey + s * 2, w, h) then menuState.selectedDifficulty, gameState.difficulty = 3, "hard"
     elseif checkButton(cx - 150, ey + s * 3 + 20, 300, 50) then menuState.currentMenu = "main" end
   elseif menuState.currentMenu == "mode" then
    local w, h, s, my = 350, 80, 130, cy - 100
    if checkButton(cx - w / 2, my, w, h) then menuState.selectedMode, gameState.gameMode = 1, "classic"
     elseif checkButton(cx - w / 2, my + s, w, h) then menuState.selectedMode, gameState.gameMode = 2, "survival"
     elseif checkButton(cx - w / 2, my + s * 2, w, h) then menuState.selectedMode, gameState.gameMode = 3, "timeAttack"
     elseif checkButton(cx - 150, my + s * 3 + 20, 300, 50) then menuState.currentMenu = "main" end
  end
end
function handleLevelUpPopupTouch(x, y)
  if not levelUpPopup.visible then
    return
  end
  local centerX = SCREEN_WIDTH / 2
  local centerY = SCREEN_HEIGHT / 2
  local popupWidth = SCREEN_WIDTH * 0.85
  local popupHeight = SCREEN_HEIGHT * 0.6
  local popupX = centerX - popupWidth / 2
  local popupY = centerY - popupHeight / 2
  local optionCount = #levelUpPopup.options
  local optionHeight = 80
  local optionSpacing = 20
  local startY = popupY + 130
  local skipButtonWidth = 120
  local skipButtonHeight = 40
  local skipButtonX = centerX - skipButtonWidth / 2
  local skipButtonY = popupY + popupHeight - 70
  if isPointInRect(x, y, skipButtonX, skipButtonY, skipButtonWidth, skipButtonHeight) then
    levelUpPopup.visible = false
    levelUpPopup.options = {}
    gameState.paused = false
    return
  end
  for i = 1, optionCount do
    local optionY = startY + (i - 1) * (optionHeight + optionSpacing)
    local optionX = popupX + 20
    local optionWidth = popupWidth - 40
    if isPointInRect(x, y, optionX, optionY, optionWidth, optionHeight) then
      applySelectedEffect(i)
      levelUpPopup.visible = false
      levelUpPopup.options = {}
      gameState.paused = false
      break
    end
  end
end
function isPointInRect(px, py, x, y, width, height)
  return px >= x and px <= x + width and py >= y and py <= y + height
end
function startGame()
  gameState.state = "playing"
  gameState.running = true
  local difficulty = difficultySettings[gameState.difficulty]
  gameState.lives = difficulty.playerLives
  config.enemy.spawnInterval = config.enemy.spawnInterval * difficulty.enemySpawnMultiplier
  gameState.score = 0
  gameState.level = config.game.startLevel
  player.x = SCREEN_WIDTH / 2 - config.player.width / 2
  player.y = SCREEN_HEIGHT - 150
  bullets = {}
  enemies = {}
  explosions = {}
  enemySpawnTimer = 0
  bulletCooldown = 0
  gameModeTimer = 0
  survivalWaveTimer = 0
  playerEffects.damageMultiplier = 1.0
  playerEffects.fireRateMultiplier = 1.0
  playerEffects.speedMultiplier = 1.0
  playerEffects.scoreMultiplier = 1.0
  playerEffects.enemySpeedMultiplier = 1.0
  playerEffects.doubleShot = false
  playerEffects.piercingShot = false
  playerEffects.invincibleTime = 0
  playerEffects.activeEffects = {}
  levelUpPopup.visible = false
  levelUpPopup.options = {}
  lastTime = System.currentTimeMillis()
end
activity.setContentView(gameView)
lastTime = System.currentTimeMillis()
gameLoop()