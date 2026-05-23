#!/bin/bash
# 端到端 smoke 测试脚本
# 用法: ./scripts/e2e.sh [BASE_URL]

BASE_URL="${1:-http://localhost:30080}"
echo "===== 智能食堂系统 E2E 测试 ====="
echo "目标: $BASE_URL"
echo ""

# 1. 注册
echo "--- 1. 注册 ---"
REGISTER_RESP=$(curl -s -X POST "$BASE_URL/api/user/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13900001111","studentNo":"2024999","password":"test123456","nickname":"E2E测试"}')
echo "$REGISTER_RESP" | python3 -m json.tool 2>/dev/null || echo "$REGISTER_RESP"

# 2. 登录
echo ""
echo "--- 2. 登录 ---"
LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/user/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13900001111","password":"test123456","loginType":"password"}')
echo "$LOGIN_RESP" | python3 -m json.tool 2>/dev/null || echo "$LOGIN_RESP"

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "Token: ${TOKEN:0:20}..."

if [ -z "$TOKEN" ]; then
  echo "!!! 登录失败，终止测试"
  exit 1
fi

AUTH="Authorization: Bearer $TOKEN"

# 3. 查询用户信息
echo ""
echo "--- 3. 查询用户信息 ---"
curl -s "$BASE_URL/api/user/users/me" -H "$AUTH" | python3 -m json.tool 2>/dev/null

# 4. 查询菜品列表
echo ""
echo "--- 4. 查询菜品列表 ---"
curl -s "$BASE_URL/api/menu/dishes?page=1&size=5" -H "$AUTH" | python3 -m json.tool 2>/dev/null

# 5. 发布今日菜单
echo ""
echo "--- 5. 发布今日菜单 ---"
TODAY=$(date +%Y-%m-%d)
curl -s -X POST "$BASE_URL/api/menu/daily" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"merchantId\":1,\"bizDate\":\"$TODAY\",\"sellStart\":\"10:30\",\"sellEnd\":\"13:30\",\"items\":[{\"dishId\":1,\"stockInit\":50},{\"dishId\":2,\"stockInit\":30}]}" | python3 -m json.tool 2>/dev/null

# 6. 下单
echo ""
echo "--- 6. 下单 ---"
ORDER_RESP=$(curl -s -X POST "$BASE_URL/api/order/orders" \
  -H "$AUTH" -H "Content-Type: application/json" -H "Idempotency-Key: e2e-test-001" \
  -d '{"merchantId":1,"items":[{"dishId":1,"quantity":2},{"dishId":2,"quantity":1}]}')
echo "$ORDER_RESP" | python3 -m json.tool 2>/dev/null || echo "$ORDER_RESP"

ORDER_ID=$(echo "$ORDER_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
echo "OrderId: $ORDER_ID"

# 7. 商家接单
echo ""
echo "--- 7. 商家接单 ---"
curl -s -X PUT "$BASE_URL/api/order/orders/$ORDER_ID/accept" -H "$AUTH" | python3 -m json.tool 2>/dev/null

# 8. 开始制作
echo ""
echo "--- 8. 开始制作 ---"
curl -s -X PUT "$BASE_URL/api/order/orders/$ORDER_ID/start" -H "$AUTH" | python3 -m json.tool 2>/dev/null

# 9. 制作完成（入队）
echo ""
echo "--- 9. 制作完成 ---"
READY_RESP=$(curl -s -X PUT "$BASE_URL/api/order/orders/$ORDER_ID/ready" -H "$AUTH")
echo "$READY_RESP" | python3 -m json.tool 2>/dev/null || echo "$READY_RESP"

PICKUP_CODE=$(echo "$READY_RESP" | grep -o '"pickupCode":"[^"]*"' | cut -d'"' -f4)
echo "PickupCode: $PICKUP_CODE"

# 10. 大屏查询
echo ""
echo "--- 10. 大屏查询 ---"
curl -s "$BASE_URL/api/pickup/queues/C01" | python3 -m json.tool 2>/dev/null

# 11. 叫号
echo ""
echo "--- 11. 叫号 ---"
curl -s -X POST "$BASE_URL/api/pickup/queues/C01/call" | python3 -m json.tool 2>/dev/null

# 12. 核销
echo ""
echo "--- 12. 核销 ---"
if [ -n "$PICKUP_CODE" ]; then
  curl -s -X POST "$BASE_URL/api/pickup/pickups/verify" \
    -H "$AUTH" -H "Content-Type: application/json" \
    -d "{\"pickupCode\":\"$PICKUP_CODE\",\"counterId\":\"C01\"}" | python3 -m json.tool 2>/dev/null
else
  echo "取餐码为空，跳过核销"
fi

# 13. 查询订单详情
echo ""
echo "--- 13. 订单详情 ---"
curl -s "$BASE_URL/api/order/orders/$ORDER_ID" -H "$AUTH" | python3 -m json.tool 2>/dev/null

echo ""
echo "===== E2E 测试完成 ====="
