const puppeteer = require('/tmp/puppeteer_render/node_modules/puppeteer');
const fs = require('fs');
const path = require('path');

const base = '/Users/tilakputta/projects/apps/healthamigoai';
const imgDir = `${base}/mobile/ios/Amigo/Images`;

const jobs = [
  { svg: `${base}/mobile/shared/assets/svg/amigo_clean.svg`, size: 40, out: `${imgDir}/amigo_profile.png` },
  { svg: `${base}/mobile/shared/assets/svg/amigo_clean.svg`, size: 80, out: `${imgDir}/amigo_profile@2x.png` },
  { svg: `${base}/mobile/shared/assets/svg/amigo_clean.svg`, size: 120, out: `${imgDir}/amigo_profile@3x.png` },
  { svg: `${base}/mobile/shared/assets/svg/amigo_chat_clean.svg`, size: 25, out: `${imgDir}/amigo_chat.png` },
  { svg: `${base}/mobile/shared/assets/svg/amigo_chat_clean.svg`, size: 50, out: `${imgDir}/amigo_chat@2x.png` },
  { svg: `${base}/mobile/shared/assets/svg/amigo_chat_clean.svg`, size: 75, out: `${imgDir}/amigo_chat@3x.png` },
];

(async () => {
  const browser = await puppeteer.launch({ headless: true, args: ['--no-sandbox'] });
  const page = await browser.newPage();

  for (const job of jobs) {
    let svgContent = fs.readFileSync(job.svg, 'utf8');
    // Ensure SVG has explicit width/height so it scales correctly
    svgContent = svgContent.replace(/<svg([^>]*)>/, (match, attrs) => {
      // Remove existing width/height attrs, add explicit ones
      attrs = attrs.replace(/\s*width="[^"]*"/, '').replace(/\s*height="[^"]*"/, '');
      return `<svg${attrs} width="${job.size}" height="${job.size}">`;
    });
    const isProfile = job.out.includes('amigo_profile');
    const html = `<!DOCTYPE html>
<html>
<head>
<style>
* { margin: 0; padding: 0; }
html, body { width: ${job.size}px; height: ${job.size}px; overflow: hidden; background: transparent; }
svg { width: ${job.size}px; height: ${job.size}px; display: block; }
</style>
</head>
<body>
${svgContent}
</body>
</html>`;

    await page.setViewport({ width: job.size, height: job.size, deviceScaleFactor: 1 });
    await page.setContent(html, { waitUntil: 'domcontentloaded' });
    await new Promise(r => setTimeout(r, 300));
    await page.screenshot({ path: job.out, omitBackground: true });
    console.log(`Saved ${job.size}x${job.size} -> ${job.out}`);
  }

  await browser.close();
})();
