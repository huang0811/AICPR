const https = require('https');
const fs = require('fs');
const WebSocket = require('ws');
const path = require('path');

// 載入你的證書和私鑰
const server = https.createServer({
	const certPath = path.join(__dirname, 'certs', 'cert.pem');
	const keyPath = path.join(__dirname, 'certs', 'key.pem');
});

// 建立 WebSocket 伺服器，綁定在 HTTPS 伺服器上
const wss = new WebSocket.Server({ server });

// 當有連接進入時觸發
wss.on('connection', (ws) => {
    console.log('Client connected');

    // 接收消息
    ws.on('message', (message) => {
        console.log(`Received: ${message}`);
        ws.send(`Server echo: ${message}`);
    });

    // 當連接關閉時觸發
    ws.on('close', () => {
        console.log('Client disconnected');
    });
});

// 伺服器監聽在 8080 端口
server.listen(8080, () => {
    console.log('HTTPS Server is running on https://localhost:8080');
});
