/**
 * 实时通讯系统前端核心逻辑
 * 负责 WebSocket 连接维护、消息渲染、业务交互及富媒体功能集成
 */
let socket = null;          // WebSocket 实例
let currentUserId = null;   // 当前登录用户 ID
let currentUserType = null; // 当前用户类型：1-普通用户 2-客服
let currentTargetId = null; // 当前正在对话的目标 ID
let heartbeatTimer = null;  // 心跳定时器句柄

const WS_URL = "ws://localhost:8888/ws"; // WebSocket 服务端地址
const HTTP_BASE_URL = "http://localhost:8080"; // HTTP 服务端地址

/**
 * 模拟登录逻辑
 * 根据用户输入的 ID 和选择的角色进入系统
 * @param {string} type USER | AGENT
 */
function loginAs(type) {
    const idInput = document.getElementById('user-id');
    const id = idInput.value;
    if (!id) return alert("请输入 ID");

    currentUserId = id;
    currentUserType = type === 'USER' ? 1 : 2;

    // 切换 UI 状态：隐藏登录面板，显示聊天主窗口
    document.getElementById('login-panel').classList.add('hidden');
    document.getElementById('chat-window').classList.remove('hidden');

    // 发起 WebSocket 连接
    initWebSocket();
}

/**
 * 初始化 WebSocket 连接并绑定事件
 */
function initWebSocket() {
    socket = new WebSocket(WS_URL);

    // 连接成功回调
    socket.onopen = () => {
        setStatus("在线", true);
        // 首先向服务端发送 AUTH 认证消息，告知身份
        socket.send(JSON.stringify({
            type: "AUTH",
            id: currentUserId,
            userType: currentUserType
        }));

        // 启动心跳机制：每隔 30 秒发送一次报活消息，防止因连接空闲被后端踢下线
        // 1. 检查并清理旧的定时器，如果 heartbeatTimer 变量里存有旧的定时器 ID，就把它停掉
        if (heartbeatTimer) clearInterval(heartbeatTimer);
        // 2. 开启一个新的定时器，并将新的 ID 赋值给变量
        heartbeatTimer = setInterval(() => {
            if (socket.readyState === WebSocket.OPEN) {
                socket.send(JSON.stringify({ type: "HEARTBEAT" }));
            }
        }, 30000);
    };

    // 接收消息回调
    socket.onmessage = (event) => {
        const data = JSON.parse(event.data);
        handleMessage(data);
    };

    // 连接关闭回调
    socket.onclose = () => {
        setStatus("已断开", false);
        // 发生断连时，立即清理心跳定时器
        if (heartbeatTimer) {
            clearInterval(heartbeatTimer);
            heartbeatTimer = null;
        }
        // 执行自动重连策略：3秒后尝试再次建立连接
        logInfo("连接断开，3秒后尝试自动重连...");
        setTimeout(initWebSocket, 3000);
    };

    // 报错处理
    socket.onerror = (err) => {
        console.error("WebSocket 通讯链路发生错误:", err);
    };
}

/**
 * 消息分发处理器
 * 根据服务端推送的消息类型执行具体的 UI 渲染或业务逻辑
 */
function handleMessage(data) {
    switch (data.type) {
        case "SYSTEM":
            // 接收系统通知：如分配客服成功、无空闲客服等
            appendMessage("系统", data.content, "other", true);
            if (data.agentId) {
                // 用户端根据系统分配的 agentId 定位对话目标
                currentTargetId = data.agentId;
                document.getElementById('target-name').innerText = "客服: " + data.agentId;
                loadHistory(); // 自动拉取与该客服的历史记录
            }
            break;
        case "RECEIVE":
            // 接收对方发送的即时消息
            if (currentUserType === 2 && !currentTargetId) {
                // 如果是客服端初次收到用户消息，则锁定对方为当前的咨询目标
                currentTargetId = data.fromId;
                document.getElementById('target-name').innerText = "用户: " + data.fromId;
                loadHistory();
            }
            appendMessage(data.fromId, data.content, "other", false, data.msgType);
            break;
    }
}

/**
 * 构建并发送文本消息
 */
function sendMessage() {
    const input = document.getElementById('msg-input');
    const content = input.value.trim();
    if (!content || !currentTargetId) return;

    const msg = {
        type: "SEND",
        toId: currentTargetId,
        content: content,
        msgType: 1 // 1 代表普通文本
    };

    // 通过 WebSocket 管道发送 JSON
    socket.send(JSON.stringify(msg));
    // 将自己发送的内容追加到 UI 界面
    appendMessage("我", content, "mine", false, 1);

    // 清空并聚焦输入框
    input.value = "";
    input.focus();
}

/**
 * 图片上传及发送流程
 * @param {HTMLInputElement} input 文件选择框 DOM
 */
async function uploadImage(input) {
    if (!input.files || !input.files[0]) return;

    // 使用 FormData 封装文件流
    const formData = new FormData();
    formData.append("file", input.files[0]);

    try {
        // 调用后端 HTTP 接口进行文件上传
        const resp = await fetch("/api/chat/upload", {
            method: "POST",
            body: formData
        });
        const path = await resp.text();

        if (path !== "error") {
            // 上传成功后得到服务器存储路径，通过 WebSocket 发送图片消息给对方
            const msg = {
                type: "SEND",
                toId: currentTargetId,
                content: path,
                msgType: 3 // 3 代表富媒体-图片
            };
            socket.send(JSON.stringify(msg));
            // 将图片在本地界面实时呈现
            appendMessage("我", path, "mine", false, 3);
        }
    } catch (e) {
        console.error("图片上传失败:", e);
    }
}

/**
 * 调用后端 API 拉取双方的最近 100 条历史聊天记录
 */
async function loadHistory() {
    if (!currentTargetId) return;
    try {
        const resp = await fetch(`/api/chat/history?userId=${currentUserId}&targetId=${currentTargetId}`);
        const history = await resp.json();
        const list = document.getElementById('message-list');
        list.innerHTML = ""; // 先清空老旧试图，重新根据历史数据渲染
        history.forEach(m => {
            const side = m.fromId == currentUserId ? "mine" : "other";
            appendMessage(m.fromId, m.content, side, false, m.msgType, m.createTime);
        });
    } catch (e) {
        console.error("拉取历史记录失败:", e);
    }
}

/**
 * 将消息组件动态插入 DOM 列表并自动滚动到底部
 * @param {string} sender 发送者标识
 * @param {string} content 消息内容
 * @param {string} side 对齐方向 (mine/other)
 * @param {boolean} isSystem 是否是系统消息
 * @param {number} msgType 消息类型 (1:文本, 3:图片)
 */
function appendMessage(sender, content, side, isSystem, msgType = 1, time = null) {
    const list = document.getElementById('message-list');
    const msgDiv = document.createElement('div');
    msgDiv.className = `msg ${side} ${isSystem ? 'system' : ''}`;

    let innerHTML = "";
    if (msgType === 3) {
        // 如果是图片，渲染为 img 标签，支持点击查看原图
        // 修复 bug: 历史记录中的图片可能是相对路径，需要拼接完整 URL
        console.log("content: ", content);
        let imgSrc = content;
        /*if (content.startsWith('/uploads/')) {
            imgSrc = HTTP_BASE_URL + content;
        }*/
        innerHTML = `<img src="${imgSrc}" alt="图片" onclick="window.open(this.src)">`;
    } else {
        // 如果是文本消息，进行基本的安全防御处理并显示
        innerHTML = `<div class="text">${content}</div>`;
    }

    const date = time ? time : (new Date()).toLocaleString();
    innerHTML += `<span class="time">${date}</span>`;
    msgDiv.innerHTML = innerHTML;

    list.appendChild(msgDiv);
    // 自动平滑滚动到最新消息位置
    list.scrollTo({ top: list.scrollHeight, behavior: 'smooth' });
}

/**
 * 更新右上角的连接状态显示
 */
function setStatus(text, online) {
    document.getElementById('connection-status').innerText = text;
    const indicator = document.querySelector('.status-indicator');
    indicator.className = `status-indicator ${online ? 'online' : ''}`;
}

/**
 * 回车键快速发送逻辑
 */
function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
}

/**
 * 控制表情面板切换显示
 */
function toggleEmoji() {
    document.getElementById('emoji-picker').classList.toggle('hidden');
}

/**
 * 选取表情并填充到输入框
 */
function addEmoji(emoji) {
    const input = document.getElementById('msg-input');
    input.value += emoji;
    toggleEmoji();
    input.focus();
}

/**
 * 退出当前登录状态（刷新页面）
 */
function logout() {
    location.reload();
}

/**
 * 辅助日志输出
 */
function logInfo(msg) {
    console.log(`[WebIM-JS] ${new Date().toLocaleTimeString()} - ${msg}`);
}
