#!/usr/bin/env node

const { execSync } = require('child_process');
const path = require('path');

const script = path.join(__dirname, '..', '..', 'scripts', 'generate-icons.ps1');

if (process.platform === 'win32') {
  execSync(`powershell -ExecutionPolicy Bypass -File "${script}"`, { stdio: 'inherit' });
}
