#!/usr/bin/env bash
# PsyChat Anthropic 代理服务测试脚本

set -u

# -------------------------------
# 配置
# -------------------------------
# 优先顺序：参数 > 环境变量 > 本地默认
PROXY_URL="${1:-${PROXY_URL:-http://localhost:8787/chat}}"

# 有效模型（可用 env 覆盖：MODEL=... ./test.sh）
MODEL="${MODEL:-claude-sonnet-4-20250514}"

# jq 存在与否
HAS_JQ=0
if command -v jq >/dev/null 2>&1; then HAS_JQ=1; fi

# 彩色输出
BOLD="\033[1m"; GREEN="\033[32m"; YELLOW="\033[33m"; RED="\033[31m"; RESET="\033[0m"

# -------------------------------
# 工具函数
# -------------------------------
print_title() { echo -e "${BOLD}$1${RESET}"; }
print_ok()    { echo -e "${GREEN}$1${RESET}"; }
print_warn()  { echo -e "${YELLOW}$1${RESET}"; }
print_err()   { echo -e "${RED}$1${RESET}"; }

# 统一 POST JSON 调用（带状态码）
post_json () {
  local url="$1"; shift
  local json_payload="$1"; shift

  # -s 静默，-D - 打印响应头到 stderr，--write-out 打印状态码到 stdout 最后一行
  # 用临时文件接收 body，便于与状态码/头分离
  local body_file
  body_file="$(mktemp)"
  local http_code
  http_code=$(curl -s -S -D /dev/stderr -o "$body_file" \
    -H "Content-Type: application/json" \
    -X POST "$url" \
    --data "$json_payload" \
    --write-out "%{http_code}")

  # 输出 body
  if [ "$HAS_JQ" -eq 1 ]; then
    jq '.' "$body_file" 2>/dev/null || cat "$body_file"
  else
    cat "$body_file"
  fi
  rm -f "$body_file"

  echo    # 换行
  echo "HTTP $http_code"
  echo
}

# -------------------------------
# 开始测试
# -------------------------------
print_title "🧪 测试 Anthropic 代理服务"
echo "目标地址: ${BOLD}${PROXY_URL}${RESET}"
echo

# 1) 基本对话
print_title "📝 测试 1: 基本对话"
post_json "$PROXY_URL" "$(cat <<JSON
{
  "model": "$MODEL",
  "messages": [
    {"role": "user", "content": "Hello! Please respond with just \"Hello back!\""}
  ],
  "max_tokens": 50
}
JSON
)"

# 2) 多轮对话
print_title "💬 测试 2: 多轮对话"
post_json "$PROXY_URL" "$(cat <<JSON
{
  "model": "$MODEL",
  "messages": [
    {"role": "user", "content": "What is 2+2?"},
    {"role": "assistant", "content": "2+2 equals 4."},
    {"role": "user", "content": "What about 3+3?"}
  ],
  "max_tokens": 50
}
JSON
)"

# 3) 错误处理 - 空消息
print_title "❌ 测试 3: 错误处理 - 空消息"
post_json "$PROXY_URL" "$(cat <<JSON
{
  "model": "$MODEL",
  "messages": [],
  "max_tokens": 50
}
JSON
)"

# 4) 错误处理 - 无效 JSON（应当返回 400）
print_title "❌ 测试 4: 错误处理 - 无效 JSON"
# 这里不用 post_json，直接看原始返回与状态码
curl -i -s -S -H "Content-Type: application/json" -X POST "$PROXY_URL" --data '{"invalid": json}'
echo
echo

# 5) CORS 预检请求（命中 Worker 应为 200 且带 CORS 头）
print_title "🌐 测试 5: CORS 预检请求"
curl -i -s -S -X OPTIONS "$PROXY_URL" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: Content-Type'
echo
echo

# 6) 流式响应（SSE）
print_title "🌊 测试 6: 流式响应 (SSE)"
print_warn "提示：下面会直接打印 SSE 数据流，按 Ctrl+C 可中断。"
curl -N -s -S -H "Content-Type: application/json" -X POST "$PROXY_URL" \
  --data "$(cat <<JSON
{
  "model": "$MODEL",
  "messages": [{"role": "user", "content": "Count from 1 to 5"}],
  "max_tokens": 100,
  "stream": true
}
JSON
)"
echo
print_ok "✅ 测试完成！"

# 使用说明（可选）
echo
echo -e "${BOLD}用法:${RESET} ./test.sh [PROXY_URL]"
echo "示例："
echo "  本地:   ./test.sh"
echo "  线上:   ./test.sh https://psychat-anthropic-proxy.psychat.workers.dev/chat"
e