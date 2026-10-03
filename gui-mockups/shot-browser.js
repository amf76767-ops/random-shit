const { chromium } = require('/tmp/gui/node_modules/playwright-core');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 } });
  await p.goto('file:///home/user/random-shit/gui-mockups/browser.html');
  await p.waitForTimeout(300);
  await p.screenshot({ path: '/home/user/random-shit/gui-mockups/browser-optionen.png' });
  await b.close();
})();
