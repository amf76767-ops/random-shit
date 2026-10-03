const { chromium } = require('/tmp/gui/node_modules/playwright-core');
(async () => {
  const b = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome', args: ['--no-sandbox'] });
  const p = await b.newPage({ viewport: { width: 1280, height: 720 } });
  for (const v of ['glas','flat','neon','card','term','pixel','paper','bp','side','spot','larp','dih']) {
    await p.goto('file:///home/user/random-shit/gui-mockups/themes.html?v=' + v);
    await p.waitForTimeout(250);
    await p.screenshot({ path: `/home/user/random-shit/gui-mockups/thema-${v}.png` });
  }
  await b.close();
})();
