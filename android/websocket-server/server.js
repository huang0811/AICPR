const https = require('https');
const fs = require('fs');
const WebSocket = require('ws');
const path = require('path');

// ���J�A���ҮѩM�p�_
const server = https.createServer({
	const certPath = path.join(__dirname, 'certs', 'cert.pem');
	const keyPath = path.join(__dirname, 'certs', 'key.pem');
});

// �إ� WebSocket ���A���A�j�w�b HTTPS ���A���W
const wss = new WebSocket.Server({ server });

// ���s���i�J��Ĳ�o
wss.on('connection', (ws) => {
    console.log('Client connected');

    // ��������
    ws.on('message', (message) => {
        console.log(`Received: ${message}`);
        ws.send(`Server echo: ${message}`);
    });

    // ��s��������Ĳ�o
    ws.on('close', () => {
        console.log('Client disconnected');
    });
});

// ���A����ť�b 8080 �ݤf
server.listen(8080, () => {
    console.log('HTTPS Server is running on https://localhost:8080');
});
