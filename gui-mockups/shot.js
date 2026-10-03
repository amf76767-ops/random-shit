const { chromium } = require('/tmp/gui/node_modules/playwright-core');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
  for (const v of ['cur','glas','flat','neon','card']) {
    await p.goto('file:///home/user/random-shit/gui-mockups/mockup.html?v=' + v);
    await p.waitForTimeout(300);
    await p.screenshot({ path: `/home/user/random-shit/gui-mockups/entwurf-${v}.png` });
  }
  await b.close();
})();
